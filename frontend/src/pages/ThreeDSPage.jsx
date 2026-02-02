import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Button from '../components/Button';
import Alert from '../components/Alert';
import { usePayment } from '../context/PaymentContext';
import { verify3DS } from '../api/paymentApi';

const ThreeDSPage = () => {
  const navigate = useNavigate();
  const { currentPayment, bankName, completePayment } = usePayment();
  const [otp, setOtp] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!currentPayment) {
      navigate('/');
    }
  }, [currentPayment, navigate]);

  const handleVerify = async () => {
    setError('');

    if (!otp || otp.length !== 6) {
      setError('Please enter the 6-digit code');
      return;
    }

    setLoading(true);

    try {
      const result = await verify3DS(currentPayment.paymentId, otp);

      if (result.success) {
        completePayment({
          paymentId: currentPayment.paymentId,
          orderId: currentPayment.orderId,
          amount: currentPayment.amount,
          providerReference: result.providerReference,
        });
        navigate('/success');
      } else {
        setError(result.message);
      }
    } catch (err) {
      setError(err.response?.data?.message || 'Verification failed');
    } finally {
      setLoading(false);
    }
  };

  if (!currentPayment) return null;

  return (
    <div className="max-w-md mx-auto">
      <div className="bg-white border border-slate-200 rounded-lg shadow-sm overflow-hidden">
        {/* Bank Header */}
        <div className="bg-slate-900 px-6 py-5 text-white">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 bg-white/10 rounded-lg flex items-center justify-center">
              <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z" />
              </svg>
            </div>
            <div>
              <h2 className="font-semibold">{bankName || 'Bank'}</h2>
              <p className="text-sm text-slate-400">3D Secure Verification</p>
            </div>
          </div>
        </div>

        <div className="p-6">
          <Alert type="error" message={error} show={!!error} />

          <p className="text-center text-sm text-slate-600 mb-6">
            Enter the 6-digit code sent to your phone
          </p>

          <input
            type="text"
            value={otp}
            onChange={(e) => setOtp(e.target.value.replace(/\D/g, '').substring(0, 6))}
            placeholder="000000"
            maxLength={6}
            className="w-full px-4 py-3 border border-slate-300 rounded-md focus:border-slate-900 focus:ring-1 focus:ring-slate-900 focus:outline-none otp-input text-center"
          />

          {/* Hint Box */}
          <div className="bg-slate-50 border border-slate-200 rounded-md p-3 mt-4 text-center">
            <span className="text-xs text-slate-600">
              Demo verification code: <span className="font-mono font-medium text-slate-900">111111</span>
            </span>
          </div>

          <Button onClick={handleVerify} disabled={loading} className="w-full mt-6">
            {loading ? 'Verifying...' : 'Confirm'}
          </Button>
        </div>
      </div>
    </div>
  );
};

export default ThreeDSPage;
