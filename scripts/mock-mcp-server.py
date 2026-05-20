"""
Mock MCP Server for testing — speaks MCP Streamable HTTP Transport protocol.
Listens on localhost:9001, serves /mcp endpoint.
Supports: initialize, tools/list, tools/call
"""
import json
import sys
import threading
import time
from http.server import HTTPServer, BaseHTTPRequestHandler

HOST = "localhost"
PORT = 9001

# Mock order database
ORDERS = {
    "12345": {"status": "运输中", "courier": "顺丰快递", "tracking": "SF1234567890", "eta": "预计明天送达"},
    "67890": {"status": "已签收", "courier": "中通快递", "tracking": "ZT9876543210", "eta": None},
}

INVENTORY = {
    "SKU-001": {"name": "无线蓝牙耳机", "stock": 152, "warehouse": "北京仓"},
    "SKU-002": {"name": "机械键盘", "stock": 3, "warehouse": "上海仓"},
    "SKU-003": {"name": "Type-C 数据线", "stock": 0, "warehouse": "深圳仓"},
}

class MockMcpHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        # Read request body
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length)
        try:
            req = json.loads(body)
        except json.JSONDecodeError:
            self.send_json({"jsonrpc": "2.0", "error": {"code": -32700, "message": "Parse error"}})
            return

        method = req.get("method", "")
        req_id = req.get("id", 0)

        if method == "initialize":
            self.handle_initialize(req_id)
        elif method == "tools/list":
            self.handle_tools_list(req_id)
        elif method == "tools/call":
            self.handle_tools_call(req_id, req.get("params", {}))
        elif method == "notifications/initialized":
            # No response needed for notifications
            self.send_response(202)
            self.end_headers()
        else:
            self.send_json({"jsonrpc": "2.0", "id": req_id, "error": {"code": -32601, "message": f"Method not found: {method}"}})

    def do_GET(self):
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(b"Mock MCP Server is running")

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Accept")
        self.end_headers()

    def handle_initialize(self, req_id):
        result = {
            "protocolVersion": "2024-11-05",
            "capabilities": {
                "tools": {}
            },
            "serverInfo": {
                "name": "Mock Order Service",
                "version": "1.0.0"
            }
        }
        self.send_json({"jsonrpc": "2.0", "id": req_id, "result": result})

    def handle_tools_list(self, req_id):
        tools = [
            {
                "name": "query_order",
                "description": "查询用户的订单状态与物流信息。传入用户ID和订单号，返回订单状态、快递公司和物流单号。",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "userId": {"type": "string", "description": "用户唯一标识"},
                        "orderId": {"type": "string", "description": "订单号"}
                    },
                    "required": ["userId", "orderId"]
                }
            },
            {
                "name": "check_inventory",
                "description": "查询商品库存信息。传入SKU编码，返回商品名称、库存数量和所在仓库。",
                "inputSchema": {
                    "type": "object",
                    "properties": {
                        "sku": {"type": "string", "description": "商品 SKU 编码"}
                    },
                    "required": ["sku"]
                }
            }
        ]
        self.send_json({"jsonrpc": "2.0", "id": req_id, "result": {"tools": tools}})

    def handle_tools_call(self, req_id, params):
        tool_name = params.get("name", "")
        arguments = params.get("arguments", {})

        if tool_name == "query_order":
            order_id = arguments.get("orderId", "")
            user_id = arguments.get("userId", "")
            order = ORDERS.get(order_id)
            if order:
                content = json.dumps({
                    "success": True,
                    "orderId": order_id,
                    "userId": user_id,
                    "status": order["status"],
                    "courier": order["courier"],
                    "trackingNumber": order["tracking"],
                    "eta": order["eta"]
                }, ensure_ascii=False)
            else:
                content = json.dumps({
                    "success": False,
                    "orderId": order_id,
                    "message": f"订单 {order_id} 不存在，请核对订单号后重试"
                }, ensure_ascii=False)

        elif tool_name == "check_inventory":
            sku = arguments.get("sku", "")
            item = INVENTORY.get(sku)
            if item:
                stock_status = "有货" if item["stock"] > 0 else "缺货"
                content = json.dumps({
                    "success": True,
                    "sku": sku,
                    "name": item["name"],
                    "stock": item["stock"],
                    "warehouse": item["warehouse"],
                    "status": stock_status
                }, ensure_ascii=False)
            else:
                content = json.dumps({
                    "success": False,
                    "sku": sku,
                    "message": f"SKU {sku} 不存在，请确认商品编码"
                }, ensure_ascii=False)
        else:
            content = json.dumps({"error": f"Unknown tool: {tool_name}"})

        result = {
            "content": [
                {"type": "text", "text": content}
            ]
        }
        self.send_json({"jsonrpc": "2.0", "id": req_id, "result": result})

    def send_json(self, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        print(f"[MOCK-MCP] {args[0]}", file=sys.stderr)


def main():
    server = HTTPServer((HOST, PORT), MockMcpHandler)
    print(f"Mock MCP Server listening on http://{HOST}:{PORT}/mcp", file=sys.stderr)
    server.serve_forever()

if __name__ == "__main__":
    main()
