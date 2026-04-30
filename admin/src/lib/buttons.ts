// Shared Tailwind class strings for buttons and form inputs. Keep these in
// one place so the visual style stays consistent across the app.

const btnBase =
  'inline-flex items-center justify-center rounded-md px-3 py-1.5 text-sm font-medium shadow-sm focus:outline-none focus:ring-2 focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50'

export const btnPrimary = `${btnBase} bg-indigo-600 text-white hover:bg-indigo-500 focus:ring-indigo-500`
export const btnOutlinePrimary = `${btnBase} border border-indigo-600 bg-white text-indigo-700 hover:bg-indigo-50 focus:ring-indigo-500`
export const btnNeutral = `${btnBase} border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 focus:ring-indigo-500`
export const btnDanger = `${btnBase} bg-red-600 text-white hover:bg-red-500 focus:ring-red-500`
export const btnSuccess = `${btnBase} bg-emerald-600 text-white hover:bg-emerald-500 focus:ring-emerald-500`
export const btnWarning = `${btnBase} bg-amber-500 text-white hover:bg-amber-400 focus:ring-amber-500`

const iconBtnBase =
  'inline-flex h-8 w-8 items-center justify-center rounded-md shadow-sm focus:outline-none focus:ring-2 focus:ring-offset-1 disabled:cursor-not-allowed disabled:opacity-50'

export const iconBtnPrimary = `${iconBtnBase} bg-indigo-600 text-white hover:bg-indigo-500 focus:ring-indigo-500`
export const iconBtnNeutral = `${iconBtnBase} border border-slate-300 bg-white text-slate-700 hover:bg-slate-50 focus:ring-indigo-500`
export const iconBtnDanger = `${iconBtnBase} bg-red-600 text-white hover:bg-red-500 focus:ring-red-500`
export const iconBtnWarning = `${iconBtnBase} bg-amber-500 text-white hover:bg-amber-400 focus:ring-amber-500`

export const inputBase =
  'block w-full rounded-md border-slate-300 text-sm shadow-sm focus:border-indigo-500 focus:ring-indigo-500'
