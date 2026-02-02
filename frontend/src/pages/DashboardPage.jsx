import { useState, useEffect } from 'react';
import Card from '../components/Card';
import Button from '../components/Button';
import StatusBadge from '../components/StatusBadge';
import { getPayments } from '../api/paymentApi';

const DashboardPage = () => {
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadPayments();
  }, []);

  const loadPayments = async () => {
    setLoading(true);
    try {
      const data = await getPayments();
      setPayments(data);
    } catch (err) {
      console.error('Failed to load payments', err);
    } finally {
      setLoading(false);
    }
  };

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleString('tr-TR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const stats = {
    total: payments.length,
    captured: payments.filter(p => p.status === 'CAPTURED').length,
    failed: payments.filter(p => p.status === 'FAILED').length,
    revenue: payments
      .filter(p => p.status === 'CAPTURED')
      .reduce((sum, p) => sum + p.amount, 0),
  };

  return (
    <div className="space-y-6">
      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Total Transactions</p>
          <p className="text-2xl font-semibold text-slate-900 mt-1">{stats.total}</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Successful</p>
          <p className="text-2xl font-semibold text-emerald-600 mt-1">{stats.captured}</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Failed</p>
          <p className="text-2xl font-semibold text-red-600 mt-1">{stats.failed}</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Total Revenue</p>
          <p className="text-2xl font-semibold text-slate-900 mt-1">
            {stats.revenue.toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' })}
          </p>
        </div>
      </div>

      {/* Transactions Table */}
      <Card 
        title="Recent Transactions" 
        actions={
          <Button onClick={loadPayments} variant="secondary">
            Refresh
          </Button>
        }
      >
        {loading ? (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-2 border-slate-900 border-t-transparent"></div>
          </div>
        ) : payments.length === 0 ? (
          <div className="text-center py-12 text-slate-500">
            <svg className="w-12 h-12 mx-auto text-slate-300 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
            </svg>
            <p className="text-sm">No transactions found</p>
          </div>
        ) : (
          <div className="overflow-x-auto -mx-6">
            <table className="w-full">
              <thead>
                <tr className="border-b border-slate-200">
                  <th className="text-left py-3 px-6 text-xs font-medium text-slate-500 uppercase tracking-wider">Order ID</th>
                  <th className="text-left py-3 px-6 text-xs font-medium text-slate-500 uppercase tracking-wider">Amount</th>
                  <th className="text-left py-3 px-6 text-xs font-medium text-slate-500 uppercase tracking-wider">Status</th>
                  <th className="text-left py-3 px-6 text-xs font-medium text-slate-500 uppercase tracking-wider">Card</th>
                  <th className="text-left py-3 px-6 text-xs font-medium text-slate-500 uppercase tracking-wider">Reference</th>
                  <th className="text-left py-3 px-6 text-xs font-medium text-slate-500 uppercase tracking-wider">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {payments.map((payment) => (
                  <tr key={payment.id} className="hover:bg-slate-50 transition-colors">
                    <td className="py-3 px-6">
                      <span className="font-mono text-sm text-slate-900">
                        {payment.orderId || '-'}
                      </span>
                    </td>
                    <td className="py-3 px-6">
                      <span className="text-sm font-medium text-slate-900">
                        {payment.amount.toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' })}
                      </span>
                    </td>
                    <td className="py-3 px-6">
                      <StatusBadge status={payment.status} />
                    </td>
                    <td className="py-3 px-6">
                      <span className="font-mono text-sm text-slate-600">
                        {payment.cardInfo || '-'}
                      </span>
                    </td>
                    <td className="py-3 px-6">
                      <span className="font-mono text-xs text-slate-500">
                        {payment.providerRef || '-'}
                      </span>
                    </td>
                    <td className="py-3 px-6">
                      <span className="text-sm text-slate-500">
                        {formatDate(payment.createdAt)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </div>
  );
};

export default DashboardPage;
