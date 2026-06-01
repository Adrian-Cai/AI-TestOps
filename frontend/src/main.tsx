import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import App from './App';
import './styles.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#2563EB',
          colorSuccess: '#16A34A',
          colorWarning: '#F59E0B',
          colorError: '#DC2626',
          colorText: '#111827',
          colorTextSecondary: '#6B7280',
          colorBorder: '#E5E7EB',
          colorBgLayout: '#F8FAFC',
          borderRadius: 8,
        },
        components: {
          Card: { borderRadiusLG: 8 },
          Button: { borderRadius: 8 },
          Table: { borderRadius: 8 },
          Drawer: { borderRadiusLG: 8 },
        },
      }}
    >
      <App />
    </ConfigProvider>
  </React.StrictMode>,
);
