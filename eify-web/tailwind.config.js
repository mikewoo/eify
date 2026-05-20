/**
 * Eify 设计系统 - Tailwind CSS 配置
 *
 * 将设计令牌映射到 Tailwind CSS
 */

/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // 主色 - 蓝紫系
        primary: {
          50: '#f0f4ff',
          100: '#e0e9ff',
          200: '#c7d5fe',
          300: '#a5b4fc',
          400: '#818cf8',
          500: '#6366f1',  // 默认主色
          600: '#4f46e5',
          700: '#4338ca',
          800: '#3730a3',
          900: '#312e81',
          DEFAULT: '#6366f1',
        },

        // 辅色 - 青薄荷系
        accent: {
          50: '#f0fdfa',
          100: '#ccfbf1',
          200: '#99f6e4',
          300: '#5eead4',
          400: '#2dd4bf',  // 默认辅色
          500: '#14b8a6',
          600: '#0d9488',
          700: '#0f766e',
          DEFAULT: '#2dd4bf',
        },

        // 功能色
        success: {
          50: '#f0fdf4',
          100: '#dcfce7',
          200: '#bbf7d0',
          300: '#86efac',
          400: '#4ade80',
          500: '#22c55e',
          600: '#16a34a',
          DEFAULT: '#22c55e',
        },

        warning: {
          50: '#fffbeb',
          100: '#fef3c7',
          200: '#fde68a',
          300: '#fcd34d',
          400: '#fbbf24',
          500: '#f59e0b',
          600: '#d97706',
          DEFAULT: '#f59e0b',
        },

        error: {
          50: '#fef2f2',
          100: '#fee2e2',
          200: '#fecaca',
          300: '#fca5a5',
          400: '#f87171',
          500: '#ef4444',
          600: '#dc2626',
          DEFAULT: '#ef4444',
        },

        info: {
          50: '#f0f9ff',
          100: '#e0f2fe',
          200: '#bae6fd',
          300: '#7dd3fc',
          400: '#38bdf8',
          500: '#0ea5e9',
          600: '#0284c7',
          DEFAULT: '#0ea5e9',
        },

        // 灰度
        gray: {
          50: '#f9fafb',
          100: '#f3f4f6',
          200: '#e5e7eb',
          300: '#d1d5db',
          400: '#9ca3af',
          500: '#6b7280',
          600: '#4b5563',
          700: '#374151',
          800: '#1f2937',
          900: '#111827',
          DEFAULT: '#6b7280',
        },
      },

      backgroundColor: {
        // 背景色
        base: '#ffffff',
        subtle: '#fafafa',
        surface: '#f3f4f6',
        'surface-raised': '#ffffff',

        // 侧边栏
        sidebar: '#0f172a',
        'sidebar-hover': '#1e293b',
        'sidebar-active': '#1e293b',

        // 输入框
        input: '#ffffff',
        'input-disabled': '#f3f4f6',
      },

      textColor: {
        // 文字色
        primary: '#0f172a',
        secondary: '#475569',
        tertiary: '#94a3b8',
        quaternary: '#cbd5e1',
        inverse: '#ffffff',
      },

      borderColor: {
        // 边框色
        DEFAULT: '#e2e8f0',
        subtle: '#f1f5f9',
        strong: '#cbd5e1',
        focus: '#6366f1',
      },

      borderRadius: {
        '4xl': '24px',
      },

      boxShadow: {
        'primary': '0 0 0 3px rgb(99 102 241 / 0.1)',
        'accent': '0 0 0 3px rgb(45 212 191 / 0.1)',
        'error': '0 0 0 3px rgb(239 68 68 / 0.1)',
      },

      backgroundImage: {
        'gradient-primary': 'linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%)',
        'gradient-accent': 'linear-gradient(135deg, #2dd4bf 0%, #14b8a6 100%)',
        'gradient-subtle': 'linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%)',
      },

      blur: {
        xs: 'blur(2px)',
      },

      spacing: {
        '18': '4.5rem',  // 72px
        '22': '5.5rem',  // 88px
        '26': '6.5rem',  // 104px
      },

      transitionTimingFunction: {
        'bounce': 'cubic-bezier(0.34, 1.56, 0.64, 1)',
      },

      transitionDuration: {
        '400': '400ms',
      },

      zIndex: {
        '60': '60',
        '70': '70',
        '80': '80',
        '90': '90',
        '100': '100',
        '110': '110',
        '120': '120',
      },
    },
  },
  plugins: [],
}
