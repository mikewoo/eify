import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ConfirmDialog from '@/components/ConfirmDialog.vue'

describe('ConfirmDialog', () => {
  function mountDialog(overrides: Record<string, any> = {}) {
    return mount(ConfirmDialog, {
      props: {
        show: true,
        message: '确定要删除此项目吗？',
        ...overrides
      }
    })
  }

  describe('渲染', () => {
    it('show=true 时渲染对话框', () => {
      const wrapper = mountDialog()
      expect(wrapper.find('.modal-overlay').exists()).toBe(true)
    })

    it('show=false 时不渲染对话框', () => {
      const wrapper = mountDialog({ show: false })
      expect(wrapper.find('.modal-overlay').exists()).toBe(false)
    })

    it('显示标题和消息', () => {
      const wrapper = mountDialog({ title: '删除确认', message: '此操作不可恢复' })
      expect(wrapper.find('.modal-title').text()).toBe('删除确认')
      expect(wrapper.find('.modal-message').text()).toBe('此操作不可恢复')
    })

    it('使用默认值', () => {
      const wrapper = mountDialog()
      expect(wrapper.find('.modal-title').text()).toBe('确认操作')
      expect(wrapper.find('.modal-btn-confirm').text()).toBe('确定')
      expect(wrapper.find('.modal-btn-cancel').text()).toBe('取消')
    })

    it('自定义按钮文字', () => {
      const wrapper = mountDialog({ confirmText: '删除', cancelText: '保留' })
      expect(wrapper.find('.modal-btn-confirm').text()).toBe('删除')
      expect(wrapper.find('.modal-btn-cancel').text()).toBe('保留')
    })
  })

  describe('事件', () => {
    it('点击确认按钮触发 confirm 事件', async () => {
      const wrapper = mountDialog()
      await wrapper.find('.modal-btn-confirm').trigger('click')
      expect(wrapper.emitted('confirm')).toHaveLength(1)
    })

    it('点击取消按钮触发 cancel 事件', async () => {
      const wrapper = mountDialog()
      await wrapper.find('.modal-btn-cancel').trigger('click')
      expect(wrapper.emitted('cancel')).toHaveLength(1)
    })

    it('点击遮罩层触发 cancel 事件', async () => {
      const wrapper = mountDialog()
      await wrapper.find('.modal-overlay').trigger('click')
      expect(wrapper.emitted('cancel')).toHaveLength(1)
    })
  })

  describe('Type 变体', () => {
    it('默认使用 danger 样式', () => {
      const wrapper = mountDialog()
      expect(wrapper.find('.btn-danger').exists()).toBe(true)
    })

    it('type=warning 使用 warning 样式', () => {
      const wrapper = mountDialog({ type: 'warning' })
      expect(wrapper.find('.btn-warning').exists()).toBe(true)
    })

    it('type=info 使用 info 样式', () => {
      const wrapper = mountDialog({ type: 'info' })
      expect(wrapper.find('.btn-info').exists()).toBe(true)
    })
  })
})
