import { Link, useSearchParams } from 'react-router-dom'

export default function InvitationResultPage() {
  const [searchParams] = useSearchParams()
  const result = searchParams.get('result')
  const isSuccess = result === 'success'
  const groupName = searchParams.get('group_name')
  const groupId = searchParams.get('group_id')
  const error = searchParams.get('error')
  const errorDescription = searchParams.get('error_description')

  if (isSuccess) {
    return (
      <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-emerald-800">Invitation Accepted</h2>
        <p className="mt-2 text-sm text-emerald-900">
          You have successfully joined <strong>{groupName || 'the group'}</strong>.
        </p>
        <div className="mt-3 flex gap-3 text-sm">
          {groupId && (
            <Link to={`/groups/${groupId}`} className="text-indigo-700 hover:underline">
              Manage this group
            </Link>
          )}
          <Link to="/" className="text-indigo-700 hover:underline">Go to dashboard</Link>
        </div>
      </div>
    )
  }
  return (
    <div className="rounded-lg border border-red-200 bg-red-50 p-6 shadow-sm">
      <h2 className="text-lg font-semibold text-red-800">
        {error ? error.replace(/_/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase()) : 'Error'}
      </h2>
      <p className="mt-2 text-sm text-red-900">
        {errorDescription || 'Something went wrong with your invitation.'}
      </p>
      <p className="mt-3 text-sm">
        <Link to="/" className="text-indigo-700 hover:underline">Go to dashboard</Link>
      </p>
    </div>
  )
}
