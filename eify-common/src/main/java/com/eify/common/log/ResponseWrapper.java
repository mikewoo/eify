package com.eify.common.log;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * HTTP 响应包装器
 * <p>
 * 用于捕获响应体内容，支持日志记录
 * 使用简单的缓存机制记录响应内容
 *
 * @author Claude
 * @since 1.0.0
 */
public class ResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream contentBuffer = new ByteArrayOutputStream();
    private ServletOutputStream outputStream;
    private PrintWriter writer;
    private boolean skipOriginal = false;

    /**
     * 构造函数
     *
     * @param response 原始响应
     */
    public ResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (outputStream == null) {
            outputStream = new CachedServletOutputStream();
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new CachedPrintWriter();
        }
        return writer;
    }

    /**
     * 获取捕获的响应体内容
     *
     * @return 响应体字符串（UTF-8 编码）
     */
    public String getCapturedResponse() {
        if (contentBuffer.size() == 0) {
            return null;
        }
        return contentBuffer.toString(StandardCharsets.UTF_8);
    }

    /**
     * 获取响应体大小（字节）
     *
     * @return 响应体大小
     */
    public int getResponseSize() {
        return contentBuffer.size();
    }

    /**
     * 刷新内容到缓冲区
     */
    public void flushContent() {
        try {
            if (writer != null) {
                writer.flush();
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * ServletOutputStream 实现
     */
    private class CachedServletOutputStream extends ServletOutputStream {
        private final ServletOutputStream original;

        public CachedServletOutputStream() {
            try {
                this.original = ResponseWrapper.super.getOutputStream();
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR);
            }
        }

        @Override
        public void write(int b) throws IOException {
            contentBuffer.write(b);
            if (!skipOriginal) {
                original.write(b);
            }
        }

        @Override
        public void write(byte[] b) throws IOException {
            contentBuffer.write(b);
            if (!skipOriginal) {
                original.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            contentBuffer.write(b, off, len);
            if (!skipOriginal) {
                original.write(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            if (!skipOriginal) {
                original.flush();
            }
        }

        @Override
        public void close() throws IOException {
            if (!skipOriginal) {
                original.close();
            }
        }

        @Override
        public boolean isReady() {
            return original.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            original.setWriteListener(writeListener);
        }
    }

    /**
     * PrintWriter 实现
     */
    private class CachedPrintWriter extends PrintWriter {
        private final PrintWriter original;

        public CachedPrintWriter() throws IOException {
            super(new OutputStreamWriter(contentBuffer, StandardCharsets.UTF_8));
            this.original = ResponseWrapper.super.getWriter();
        }

        @Override
        public void write(char[] buf, int off, int len) {
            super.write(buf, off, len);
            flushQuietly();
            if (!skipOriginal) {
                original.write(buf, off, len);
            }
        }

        @Override
        public void write(String s, int off, int len) {
            super.write(s, off, len);
            flushQuietly();
            if (!skipOriginal) {
                original.write(s, off, len);
            }
        }

        @Override
        public void write(int c) {
            super.write(c);
            flushQuietly();
            if (!skipOriginal) {
                original.write(c);
            }
        }

        @Override
        public void flush() {
            super.flush();
            if (!skipOriginal) {
                original.flush();
            }
        }

        @Override
        public void close() {
            super.close();
            if (!skipOriginal) {
                original.close();
            }
        }

        private void flushQuietly() {
            try {
                super.flush();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
}
