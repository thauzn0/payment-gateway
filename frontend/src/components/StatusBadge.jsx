const StatusBadge = ({ status }) => {
  const styles = {
    CREATED: 'bg-slate-100 text-slate-700',
    AUTHORIZED: 'bg-amber-50 text-amber-700',
    CAPTURED: 'bg-emerald-50 text-emerald-700',
    REFUNDED: 'bg-violet-50 text-violet-700',
    PARTIALLY_REFUNDED: 'bg-violet-50 text-violet-700',
    FAILED: 'bg-red-50 text-red-700',
    CANCELLED: 'bg-slate-100 text-slate-600',
  };

  const labels = {
    CREATED: 'Created',
    AUTHORIZED: 'Authorized',
    CAPTURED: 'Captured',
    REFUNDED: 'Refunded',
    PARTIALLY_REFUNDED: 'Partial Refund',
    FAILED: 'Failed',
    CANCELLED: 'Cancelled',
  };

  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded text-xs font-medium ${styles[status] || 'bg-slate-100 text-slate-700'}`}>
      {labels[status] || status}
    </span>
  );
};

export default StatusBadge;
