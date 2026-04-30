import { Link } from 'react-router-dom'

export default function NotFound() {
  return (
    <div className="rounded-md border border-slate-200 bg-white p-6 text-sm text-slate-700 shadow-sm">
      Page not found.{' '}
      <Link to="/" className="text-indigo-600 hover:underline">Go home</Link>.
    </div>
  )
}
