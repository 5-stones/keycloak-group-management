import { createRoot } from 'react-dom/client'
import App from './App'
import './index.css'

// NOTE: React StrictMode is intentionally NOT used here. StrictMode double-
// invokes effects in development, which breaks the OIDC authorization-code
// exchange (the code is single-use; the second invocation throws and the
// error path retriggers signinRedirect → infinite loop). If StrictMode is
// needed in the future, gate the callback handler against re-entry first.
const root = document.getElementById('root')
if (!root) throw new Error('Root element #root not found')
createRoot(root).render(<App />)
