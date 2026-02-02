import { useState, useEffect } from 'react';
import Card from '../components/Card';
import Button from '../components/Button';
import { getApiLogs } from '../api/paymentApi';

const ApiLogsPage = () => {
  const [logs, setLogs] = useState([]);
  const [loading, setLoading] = useState(true);
  const [expandedLog, setExpandedLog] = useState(null);

  useEffect(() => {
    loadLogs();
  }, []);

  const loadLogs = async () => {
    setLoading(true);
    try {
      const data = await getApiLogs();
      setLogs(data);
    } catch (err) {
      console.error('Failed to load logs', err);
    } finally {
      setLoading(false);
    }
  };

  const formatJson = (str) => {
    if (!str) return '';
    try {
      return JSON.stringify(JSON.parse(str), null, 2);
    } catch {
      return str;
    }
  };

  const formatDate = (dateString) => {
    return new Date(dateString).toLocaleString('tr-TR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  };

  const getMethodStyle = (method) => {
    const styles = {
      GET: 'bg-blue-600',
      POST: 'bg-emerald-600',
      PUT: 'bg-amber-600',
      DELETE: 'bg-red-600',
      PATCH: 'bg-violet-600',
    };
    return styles[method] || 'bg-slate-600';
  };

  const getStatusStyle = (status) => {
    if (status >= 200 && status < 300) return 'text-emerald-700 bg-emerald-50';
    if (status >= 400 && status < 500) return 'text-amber-700 bg-amber-50';
    if (status >= 500) return 'text-red-700 bg-red-50';
    return 'text-slate-700 bg-slate-50';
  };

  const stats = {
    total: logs.length,
    success: logs.filter(l => l.responseStatus >= 200 && l.responseStatus < 300).length,
    error: logs.filter(l => l.responseStatus >= 400).length,
    avgLatency: Math.round(logs.reduce((sum, l) => sum + (l.latencyMs || 0), 0) / logs.length || 0),
  };

  return (
    <div className="space-y-6">
      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Total Requests</p>
          <p className="text-2xl font-semibold text-slate-900 mt-1">{stats.total}</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Success (2xx)</p>
          <p className="text-2xl font-semibold text-emerald-600 mt-1">{stats.success}</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Errors (4xx/5xx)</p>
          <p className="text-2xl font-semibold text-red-600 mt-1">{stats.error}</p>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg p-4">
          <p className="text-xs font-medium text-slate-500 uppercase tracking-wider">Avg Latency</p>
          <p className="text-2xl font-semibold text-slate-900 mt-1">{stats.avgLatency}ms</p>
        </div>
      </div>

      {/* Logs List */}
      <Card 
        title="API Request Logs" 
        actions={
          <Button onClick={loadLogs} variant="secondary">
            Refresh
          </Button>
        }
      >
        {loading ? (
          <div className="flex items-center justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-2 border-slate-900 border-t-transparent"></div>
          </div>
        ) : logs.length === 0 ? (
          <div className="text-center py-12 text-slate-500">
            <svg className="w-12 h-12 mx-auto text-slate-300 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <p className="text-sm">No API logs found</p>
          </div>
        ) : (
          <div className="space-y-2 -mx-6 px-6">
            {logs.map((log) => (
              <div
                key={log.id}
                className="border border-slate-200 rounded-lg overflow-hidden hover:border-slate-300 transition-colors"
              >
                {/* Header */}
                <div
                  className="flex items-center justify-between p-3 cursor-pointer bg-white"
                  onClick={() => setExpandedLog(expandedLog === log.id ? null : log.id)}
                >
                  <div className="flex items-center gap-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium text-white ${getMethodStyle(log.method)}`}>
                      {log.method}
                    </span>
                    <span className="font-mono text-sm text-slate-700">{log.endpoint}</span>
                  </div>
                  <div className="flex items-center gap-3">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${getStatusStyle(log.responseStatus)}`}>
                      {log.responseStatus}
                    </span>
                    <span className="text-xs text-slate-500 font-mono">{log.latencyMs}ms</span>
                    <svg 
                      className={`w-4 h-4 text-slate-400 transition-transform ${expandedLog === log.id ? 'rotate-180' : ''}`} 
                      fill="none" 
                      stroke="currentColor" 
                      viewBox="0 0 24 24"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                    </svg>
                  </div>
                </div>

                {/* Expanded Content */}
                {expandedLog === log.id && (
                  <div className="border-t border-slate-200 p-4 space-y-4 bg-slate-50">
                    {/* Meta Info */}
                    <div className="flex flex-wrap gap-x-6 gap-y-2 text-xs text-slate-500">
                      <span>
                        <span className="font-medium text-slate-600">Correlation ID:</span> {log.correlationId}
                      </span>
                      {log.paymentId && (
                        <span>
                          <span className="font-medium text-slate-600">Payment:</span> {log.paymentId.substring(0, 8)}...
                        </span>
                      )}
                      <span>
                        <span className="font-medium text-slate-600">Time:</span> {formatDate(log.createdAt)}
                      </span>
                    </div>

                    {/* Request Body */}
                    {log.requestBody && (
                      <div>
                        <h4 className="text-xs font-medium text-slate-600 mb-2 uppercase tracking-wider">
                          Request Body
                        </h4>
                        <pre className="bg-slate-900 text-slate-100 p-3 rounded-md overflow-x-auto text-xs font-mono">
                          {formatJson(log.requestBody)}
                        </pre>
                      </div>
                    )}

                    {/* Response Body */}
                    {log.responseBody && (
                      <div>
                        <h4 className="text-xs font-medium text-slate-600 mb-2 uppercase tracking-wider">
                          Response Body
                        </h4>
                        <pre className="bg-slate-900 text-slate-100 p-3 rounded-md overflow-x-auto text-xs font-mono max-h-48 overflow-y-auto">
                          {formatJson(log.responseBody)}
                        </pre>
                      </div>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  );
};

export default ApiLogsPage;
