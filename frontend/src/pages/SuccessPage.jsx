import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Card from '../components/Card';
import { usePayment } from '../context/PaymentContext';

const SuccessPage = () => {
  const navigate = useNavigate();
  const { successData, resetPayment } = usePayment();
  const [countdown, setCountdown] = useState(5);

  useEffect(() => {
    if (!successData) {
      navigate('/');
      return;
    }

    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          resetPayment();
          navigate('/dashboard');
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [successData, navigate, resetPayment]);

  if (!successData) return null;

  return (
    <div className="max-w-md mx-auto">
      <Card>
        <div className="text-center">
          {/* Success Icon */}
          <div className="w-16 h-16 bg-emerald-100 rounded-full flex items-center justify-center mx-auto mb-4">
            <svg className="w-8 h-8 text-emerald-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            </svg>
          </div>

          <h2 className="text-xl font-semibold text-slate-900 mb-1">
            Payment Successful
          </h2>
          <p className="text-sm text-slate-500 mb-6">Your order has been confirmed.</p>
        </div>

        {/* Order Details */}
        <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 space-y-3">
          <div className="flex justify-between text-sm">
            <span className="text-slate-500">Order ID</span>
            <span className="font-mono text-slate-900">
              {successData.paymentId?.substring(0, 8)}...
            </span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-slate-500">Amount</span>
            <span className="font-medium text-slate-900">
              {successData.amount?.toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' })}
            </span>
          </div>
          <div className="flex justify-between text-sm">
            <span className="text-slate-500">Reference</span>
            <span className="font-mono text-xs text-slate-700">
              {successData.providerReference}
            </span>
          </div>
        </div>

        {/* Countdown */}
        <div className="mt-6 text-center">
          <p className="text-xs text-slate-400">Redirecting to dashboard in {countdown}s</p>
        </div>
      </Card>
    </div>
  );
};

export default SuccessPage;
