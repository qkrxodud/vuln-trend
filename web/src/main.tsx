import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import { createDataClient } from './api/data-client.ts'
import App from './App.tsx'
import { DataProvider } from './hooks/DataProvider.tsx'
import './index.css'
import { restoreSpaPath } from './spa-redirect.ts'

restoreSpaPath()

const rootElement = document.getElementById('root')
if (rootElement === null) throw new Error('Root element #root not found')

const dataClient = createDataClient(window.fetch.bind(window))

createRoot(rootElement).render(
  <StrictMode>
    <DataProvider client={dataClient}>
      <BrowserRouter basename={import.meta.env.BASE_URL}>
        <App />
      </BrowserRouter>
    </DataProvider>
  </StrictMode>,
)
