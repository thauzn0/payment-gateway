import { useState, useEffect } from 'react';
import Card from '../components/Card';
import Button from '../components/Button';
import StatusBadge from '../components/StatusBadge';
import { getPayments, getDashboardMetrics } from '../api/paymentApi';

const DashboardPage = () => {
  const [payments, setPayments] = useState([]);
  const [metrics, setMetrics] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('overview');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [paymentsData, metricsData] = await Promise.all([
        getPayments(),
        getDashboardMetrics()
      ]);
      setPayments(paymentsData);
      setMetrics(metricsData);
    } catch (err) {
      console.error('Failed to load data', err);
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

  const formatCurrency = (amount) => {
    return (amount || 0).toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' });
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="animate-spin rounded-full h-8 w-8 border-2 border-slate-900 border-t-transparent"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Tab Navigation */}
      <div className="flex gap-1 bg-slate-100 p-1 rounded-lg w-fit">
        {['overview', 'transactions', 'performance'].map((tab) => (
          <button
            key={tab}
            onClick={() => setActiveTab(tab)}
            className={`px-4 py-2 text-sm font-medium rounded-md transition-colors ${
              activeTab === tab
                ? 'bg-white text-slate-900 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            {tab === 'overview' && 'Overview'}
            {tab === 'transactions' && 'Transactions'}
            {tab === 'performance' && 'Performance'}
          </button>
        ))}
        <Button onClick={loadData} variant="secondary" className="ml-2">
          Refresh
        </Button>
      </div>

      {/* Overview Tab */}
      {activeTab === 'overview' && metrics && (
        <>
          {/* Key Metrics */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Total Revenue</p>
              <p className="text-2xl font-semibold text-slate-900 mt-1">{formatCurrency(metrics.totalRevenue)}</p>
            </div>
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Commission Paid</p>
              <p className="text-2xl font-semibold text-red-600 mt-1">-{formatCurrency(metrics.totalCommission)}</p>
            </div>
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Net Revenue</p>
              <p className="text-2xl font-semibold text-emerald-600 mt-1">{formatCurrency(metrics.netRevenue)}</p>
            </div>
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Success Rate</p>
              <p className="text-2xl font-semibold text-slate-900 mt-1">{metrics.successRate.toFixed(1)}%</p>
            </div>
          </div>

          {/* Secondary Metrics */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Total Transactions</p>
              <p className="text-xl font-semibold text-slate-900 mt-1">{metrics.totalPayments}</p>
            </div>
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Successful</p>
              <p className="text-xl font-semibold text-emerald-600 mt-1">{metrics.capturedPayments}</p>
            </div>
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Failed</p>
              <p className="text-xl font-semibold text-red-600 mt-1">{metrics.failedPayments}</p>
            </div>
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Avg Latency</p>
              <p className="text-xl font-semibold text-slate-900 mt-1">{metrics.avgLatency.toFixed(0)}ms</p>
            </div>
          </div>

          {/* Provider Distribution */}
          <Card title="Provider Distribution">
            {metrics.providerDistribution && Object.keys(metrics.providerDistribution).length > 0 ? (
              <div className="space-y-3">
                {Object.entries(metrics.providerDistribution).map(([provider, count]) => {
                  const total = Object.values(metrics.providerDistribution).reduce((a, b) => a + b, 0);
                  const percentage = ((count / total) * 100).toFixed(1);
                  return (
                    <div key={provider} className="flex items-center gap-4">
                      <div className="w-32 text-sm font-medium text-slate-700">{provider}</div>
                      <div className="flex-1 bg-slate-100 rounded-full h-2">
                        <div
                          className="bg-slate-900 h-2 rounded-full"
                          style={{ width: `${percentage}%` }}
                        />
                      </div>
                      <div className="w-20 text-right text-sm text-slate-600">
                        {count} ({percentage}%)
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <p className="text-sm text-slate-500">No provider data available</p>
            )}
          </Card>
        </>
      )}

      {/* Transactions Tab */}
      {activeTab === 'transactions' && (
        <Card title="Transaction Details">
          {payments.length === 0 ? (
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
                    <th className="text-left py-3 px-6 text-xs font-medium text-slate-500 uppercase tracking-wider">Gross Amount</th>
                    <th className="text-left py-3 px-6 text-xs font-medium text-slate-500 uppercase tracking-wider">Commission</th>
                    <th className="text-left py-3 px-6 text-xs font-medium text-slate-500 uppercase tracking-wider">Net Amount</th>
                    <th className="text-left py-3 px-6 text-xs font-medium text-slate-500 uppercase tracking-wider">Status</th>
                    <th className="text-left py-3 px-6 text-xs font-medium text-slate-500 uppercase tracking-wider">Provider</th>
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
                          {formatCurrency(payment.amount)}
                        </span>
                      </td>
                      <td className="py-3 px-6">
                        {payment.commissionAmount ? (
                          <div>
                            <span className="text-sm font-medium text-red-600">
                              -{formatCurrency(payment.commissionAmount)}
                            </span>
                            <span className="text-xs text-slate-400 ml-1">
                              ({payment.commissionRate}%)
                            </span>
                          </div>
                        ) : (
                          <span className="text-sm text-slate-400">-</span>
                        )}
                      </td>
                      <td className="py-3 px-6">
                        {payment.netAmount ? (
                          <span className="text-sm font-medium text-emerald-600">
                            {formatCurrency(payment.netAmount)}
                          </span>
                        ) : (
                          <span className="text-sm text-slate-400">-</span>
                        )}
                      </td>
                      <td className="py-3 px-6">
                        <StatusBadge status={payment.status} />
                      </td>
                      <td className="py-3 px-6">
                        <span className="text-sm text-slate-600">
                          {payment.providerName || '-'}
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
      )}

      {/* Performance Tab */}
      {activeTab === 'performance' && metrics && (
        <>
          {/* API Metrics */}
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Total Requests</p>
              <p className="text-2xl font-semibold text-slate-900 mt-1">{metrics.totalRequests}</p>
            </div>
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Success (2xx)</p>
              <p className="text-2xl font-semibold text-emerald-600 mt-1">{metrics.successRequests}</p>
            </div>
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Errors (4xx/5xx)</p>
              <p className="text-2xl font-semibold text-red-600 mt-1">{metrics.errorRequests}</p>
            </div>
            <div className="bg-white border border-slate-200 rounded-lg p-4">
              <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Error Rate</p>
              <p className="text-2xl font-semibold text-slate-900 mt-1">{metrics.errorRate.toFixed(2)}%</p>
            </div>
          </div>

          {/* Latency Metrics */}
          <Card title="Response Latency">
            <div className="grid grid-cols-3 gap-6">
              <div className="text-center p-4 bg-slate-50 rounded-lg">
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-2">P50 (Median)</p>
                <p className="text-3xl font-semibold text-slate-900">{metrics.p50Latency}ms</p>
              </div>
              <div className="text-center p-4 bg-slate-50 rounded-lg">
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-2">P95</p>
                <p className="text-3xl font-semibold text-amber-600">{metrics.p95Latency}ms</p>
              </div>
              <div className="text-center p-4 bg-slate-50 rounded-lg">
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-2">P99</p>
                <p className="text-3xl font-semibold text-red-600">{metrics.p99Latency}ms</p>
              </div>
            </div>
          </Card>

          {/* Payment Success Rate */}
          <Card title="Payment Metrics">
            <div className="grid grid-cols-2 gap-6">
              <div>
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-2">Payment Success Rate</p>
                <div className="flex items-end gap-2">
                  <p className="text-4xl font-semibold text-emerald-600">{metrics.successRate.toFixed(1)}%</p>
                  <p className="text-sm text-slate-500 mb-1">
                    ({metrics.capturedPayments} / {metrics.totalPayments})
                  </p>
                </div>
                <div className="mt-3 bg-slate-100 rounded-full h-3">
                  <div
                    className="bg-emerald-500 h-3 rounded-full"
                    style={{ width: `${metrics.successRate}%` }}
                  />
                </div>
              </div>
              <div>
                <p className="text-xs font-medium text-slate-500 uppercase tracking-wider mb-2">Commission Overview</p>
                <div className="space-y-2">
                  <div className="flex justify-between text-sm">
                    <span className="text-slate-600">Gross Revenue</span>
                    <span className="font-medium">{formatCurrency(metrics.totalRevenue)}</span>
                  </div>
                  <div className="flex justify-between text-sm">
                    <span className="text-slate-600">Total Commission</span>
                    <span className="font-medium text-red-600">-{formatCurrency(metrics.totalCommission)}</span>
                  </div>
                  <div className="border-t border-slate-200 pt-2 flex justify-between text-sm">
                    <span className="text-slate-900 font-medium">Net Revenue</span>
                    <span className="font-semibold text-emerald-600">{formatCurrency(metrics.netRevenue)}</span>
                  </div>
                </div>
              </div>
            </div>
          </Card>
        </>
      )}
    </div>
  );
};

export default DashboardPage;
