# Eify 公共组件使用示例

## EifyTable.vue - 通用表格组件

### 基础用法

```vue
<script setup lang="ts">
import { ref } from 'vue'
import EifyTable from '@/components/EifyTable.vue'

// 定义列配置
const columns = [
  { prop: 'name', label: '名称', width: 200 },
  { prop: 'status', label: '状态', width: 100 },
  { prop: 'createTime', label: '创建时间' }
]

// API 方法
const fetchData = async ({ page, size }) => {
  const res = await api.getUsers({ page, size })
  return {
    records: res.data.list,
    total: res.data.total
  }
}

// 表格引用
const tableRef = ref()

// 刷新表格
const handleRefresh = () => {
  tableRef.value?.refresh()
}
</script>

<template>
  <EifyTable
    ref="tableRef"
    :columns="columns"
    :api="fetchData"
  />
</template>
```

### 带操作列

```vue
<template>
  <EifyTable
    ref="tableRef"
    :columns="columns"
    :api="fetchData"
    action-width="180"
  >
    <template #actions="{ row }">
      <el-button link type="primary" @click="handleEdit(row)">
        编辑
      </el-button>
      <el-button link type="danger" @click="handleDelete(row)">
        删除
      </el-button>
    </template>
  </EifyTable>
</template>
```

### 自定义插槽列

```vue
<script setup lang="ts">
const columns = [
  { prop: 'name', label: '名称' },
  { prop: 'status', label: '状态', slot: 'status' },
  { prop: 'avatar', label: '头像', slot: 'avatar' }
]
</script>

<template>
  <EifyTable :columns="columns" :api="fetchData">
    <template #status="{ row }">
      <el-tag :type="row.status === 1 ? 'success' : 'danger'">
        {{ row.status === 1 ? '启用' : '禁用' }}
      </el-tag>
    </template>

    <template #avatar="{ row }">
      <el-avatar :src="row.avatar" />
    </template>

    <template #actions="{ row }">
      <el-button link @click="handleEdit(row)">编辑</el-button>
    </template>
  </EifyTable>
</template>
```

---

## EifyFormDialog.vue - 通用表单弹窗

### 基础用法

```vue
<script setup lang="ts">
import { ref } from 'vue'
import EifyFormDialog from '@/components/EifyFormDialog.vue'
import { notifySuccess, notifyError } from '@/utils/notify'

const dialogRef = ref()
const formData = ref({
  name: '',
  email: ''
})

// 表单验证规则
const rules = {
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱', trigger: 'blur' }
  ]
}

// 打开新增弹窗
const handleAdd = () => {
  dialogRef.value?.open()
}

// 打开编辑弹窗
const handleEdit = (row: any) => {
  dialogRef.value?.open(row)
}

// 提交表单
const handleSubmit = async (data: any, mode: string) => {
  try {
    if (mode === 'add') {
      await api.createUser(data)
    } else {
      await api.updateUser(data.id, data)
    }
    notifySuccess(mode === 'add' ? '创建成功' : '更新成功')
    dialogRef.value?.submitSuccess()
    // 刷新表格
    tableRef.value?.refresh()
  } catch (error) {
    notifyError('操作失败')
    dialogRef.value?.submitFail()
  }
}
</script>

<template>
  <div>
    <el-button type="primary" @click="handleAdd">新增</el-button>

    <EifyFormDialog
      ref="dialogRef"
      v-model="dialogVisible"
      :rules="rules"
      :default-data="formData"
      dialog-props="{
        addTitle: '新增用户',
        editTitle: '编辑用户'
      }"
      @submit="handleSubmit"
    >
      <template #form="{ data }">
        <el-form-item label="名称" prop="name">
          <el-input v-model="data.name" placeholder="请输入名称" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="data.email" placeholder="请输入邮箱" />
        </el-form-item>
      </template>
    </EifyFormDialog>
  </div>
</template>
```

---

## useConfirm.ts - 删除确认

### 基础用法

```vue
<script setup lang="ts">
import { useConfirm } from '@/composables/useConfirm'
import { deleteUser } from '@/api/user'

// 创建删除确认
const confirmDelete = useConfirm(deleteUser, {
  message: '确定要删除此用户吗？删除后无法恢复。',
  successMessage: '用户已删除'
})

// 使用
const handleDelete = async (id: number) => {
  const success = await confirmDelete(id)
  if (success) {
    // 刷新列表
    refresh()
  }
}
</script>

<template>
  <el-button type="danger" @click="handleDelete(row.id)">
    删除
  </el-button>
</template>
```

### 批量删除

```vue
<script setup lang="ts">
import { useBatchConfirm } from '@/composables/useConfirm'
import { deleteUsers } from '@/api/user'

const confirmBatchDelete = useBatchConfirm(deleteUsers, {
  message: '确定要删除选中的用户吗？',
  successMessage: '批量删除成功'
})

const handleBatchDelete = async (ids: number[]) => {
  const success = await confirmBatchDelete(ids)
  if (success) {
    refresh()
  }
}
</script>
```

---

## useRequest.ts - 请求状态管理

### 基础用法

```vue
<script setup lang="ts">
import { useRequest } from '@/composables/useRequest'
import { getUserDetail } from '@/api/user'

const { data, loading, error, execute } = useRequest(getUserDetail)

// 手动执行
const loadUser = () => {
  execute(userId)
}

// 立即执行
const { data: userData } = useRequest(getUserDetail, {
  immediate: true
})
</script>

<template>
  <div v-if="loading">加载中...</div>
  <div v-else-if="error">加载失败</div>
  <div v-else>{{ data }}</div>
</template>
```

### 带回调

```vue
<script setup lang="ts">
const { data, loading, execute } = useRequest(getUserDetail, {
  onSuccess: (data) => {
    console.log('加载成功', data)
  },
  onError: (error) => {
    console.error('加载失败', error)
  }
})
</script>
```

---

## notify.ts - 统一通知

### 基础用法

```vue
<script setup lang="ts">
import {
  notifySuccess,
  notifyError,
  notifyWarning,
  notifyInfo
} from '@/utils/notify'

const handleSuccess = () => {
  notifySuccess('操作成功')
}

const handleError = () => {
  notifyError('操作失败')
}

const handleWarning = () => {
  notifyWarning('请注意')
}

const handleInfo = () => {
  notifyInfo('提示信息')
}
</script>
```

### 确认对话框

```vue
<script setup lang="ts">
import { confirmDialog } from '@/utils/notify'

const handleConfirm = async () => {
  const confirmed = await confirmDialog('确定要执行此操作吗？')
  if (confirmed) {
    // 执行操作
  }
}
</script>
```

---

## 完整示例：用户管理页面

```vue
<script setup lang="ts">
import { ref } from 'vue'
import EifyTable from '@/components/EifyTable.vue'
import EifyFormDialog from '@/components/EifyFormDialog.vue'
import { useConfirm } from '@/composables/useConfirm'
import { notifySuccess, notifyError } from '@/utils/notify'
import * as api from '@/api/user'

// 表格配置
const columns = [
  { prop: 'username', label: '用户名', width: 150 },
  { prop: 'email', label: '邮箱', width: 200 },
  { prop: 'role', label: '角色', width: 120, slot: 'role' },
  { prop: 'status', label: '状态', width: 100, slot: 'status' },
  { prop: 'createTime', label: '创建时间' }
]

// 获取用户列表
const fetchUsers = async ({ page, size }) => {
  const res = await api.getUserList({ page, size })
  return {
    records: res.data.list,
    total: res.data.total
  }
}

const tableRef = ref()
const dialogRef = ref()

// 删除确认
const confirmDelete = useConfirm(api.deleteUser, {
  message: '确定要删除此用户吗？',
  successMessage: '删除成功'
})

// 打开新增弹窗
const handleAdd = () => {
  dialogRef.value?.open()
}

// 打开编辑弹窗
const handleEdit = (row: any) => {
  dialogRef.value?.open(row)
}

// 删除用户
const handleDelete = async (id: number) => {
  const success = await confirmDelete(id)
  if (success) {
    tableRef.value?.refresh()
  }
}

// 表单提交
const handleSubmit = async (data: any, mode: string) => {
  try {
    if (mode === 'add') {
      await api.createUser(data)
      notifySuccess('创建成功')
    } else {
      await api.updateUser(data.id, data)
      notifySuccess('更新成功')
    }
    dialogRef.value?.submitSuccess()
    tableRef.value?.refresh()
  } catch (error) {
    notifyError('操作失败')
    dialogRef.value?.submitFail()
  }
}
</script>

<template>
  <PageLayout title="用户管理" description="管理系统用户和权限">
    <template #actions>
      <el-button type="primary" @click="handleAdd">
        新增用户
      </el-button>
    </template>

    <EifyTable
      ref="tableRef"
      :columns="columns"
      :api="fetchUsers"
    >
      <template #role="{ row }">
        <el-tag>{{ row.role }}</el-tag>
      </template>

      <template #status="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'info'">
          {{ row.status === 1 ? '正常' : '禁用' }}
        </el-tag>
      </template>

      <template #actions="{ row }">
        <el-button link type="primary" @click="handleEdit(row)">
          编辑
        </el-button>
        <el-button link type="danger" @click="handleDelete(row.id)">
          删除
        </el-button>
      </template>
    </EifyTable>

    <EifyFormDialog
      ref="dialogRef"
      :rules="{
        username: [
          { required: true, message: '请输入用户名', trigger: 'blur' }
        ],
        email: [
          { required: true, message: '请输入邮箱', trigger: 'blur' }
        ]
      }"
      :default-data="{ username: '', email: '', role: '', status: 1 }"
      @submit="handleSubmit"
    >
      <template #form="{ data }">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="data.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="data.email" placeholder="请输入邮箱" />
        </el-form-item>

        <el-form-item label="角色" prop="role">
          <el-select v-model="data.role" placeholder="请选择角色">
            <el-option label="管理员" value="admin" />
            <el-option label="普通用户" value="user" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="data.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </template>
    </EifyFormDialog>
  </PageLayout>
</template>
```
