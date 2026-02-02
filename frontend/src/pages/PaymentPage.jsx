import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import Card from '../components/Card';
import Button from '../components/Button';
import Input from '../components/Input';
import Alert from '../components/Alert';
import { usePayment } from '../context/PaymentContext';
import { getTestCards, processPayment } from '../api/paymentApi';

const PaymentPage = () => {
  const navigate = useNavigate();
  const { currentPayment, setBank } = usePayment();
  const [testCards, setTestCards] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [cardNumber, setCardNumber] = useState('');
  const [cardHolder, setCardHolder] = useState('');
  const [expiryMonth, setExpiryMonth] = useState('');
  const [expiryYear, setExpiryYear] = useState('');
  const [cvv, setCvv] = useState('');

  useEffect(() => {
    if (!currentPayment) {
      navigate('/');
      return;
    }

    loadTestCards();
  }, [currentPayment, navigate]);

  const loadTestCards = async () => {
    try {
      const cards = await getTestCards();
      setTestCards(cards);
    } catch (err) {
      console.error('Failed to load test cards', err);
    }
  };

  const formatCardNumber = (value) => {
    const cleaned = value.replace(/\D/g, '');
    const formatted = cleaned.replace(/(.{4})/g, '$1 ').trim();
    return formatted.substring(0, 19);
  };

  const fillCard = (card) => {
    setCardNumber(formatCardNumber(card.fullNumber));
    setCardHolder(card.holder);
    setExpiryMonth(card.expiryMonth);
    setExpiryYear(card.expiryYear);
    setCvv(card.cvv);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!cardNumber || !cardHolder || !expiryMonth || !expiryYear || !cvv) {
      setError('Please fill in all fields');
      return;
    }

    setLoading(true);

    try {
      const result = await processPayment(currentPayment.paymentId, {
        cardNumber: cardNumber.replace(/\s/g, ''),
        cardHolder,
        expiryMonth,
        expiryYear,
        cvv,
      });

      if (result.status === 'REQUIRES_3DS') {
        setBank(result.bankName);
        navigate('/3ds');
      } else if (result.status === 'FAILED') {
        setError(result.message || 'Payment failed');
      }
    } catch (err) {
      setError(err.response?.data?.message || 'An error occurred');
    } finally {
      setLoading(false);
    }
  };

  if (!currentPayment) return null;

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      {/* Payment Form */}
      <div className="lg:col-span-2">
        <Card title="Payment Details">
          <Alert type="error" message={error} show={!!error} />

          {/* Payment Summary */}
          <div className="bg-slate-50 border border-slate-200 rounded-lg p-4 mb-6">
            <div className="flex justify-between items-center">
              <span className="text-sm text-slate-600">{currentPayment.productName}</span>
              <span className="text-lg font-semibold text-slate-900">
                {currentPayment.amount.toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' })}
              </span>
            </div>
          </div>

          <form onSubmit={handleSubmit}>
            <Input
              label="Card Number"
              value={cardNumber}
              onChange={(e) => setCardNumber(formatCardNumber(e.target.value))}
              placeholder="0000 0000 0000 0000"
              maxLength={19}
              inputClassName="card-number-input"
            />

            <Input
              label="Cardholder Name"
              value={cardHolder}
              onChange={(e) => setCardHolder(e.target.value.toUpperCase())}
              placeholder="NAME SURNAME"
            />

            <div className="grid grid-cols-3 gap-4">
              <Input
                label="Month"
                value={expiryMonth}
                onChange={(e) => setExpiryMonth(e.target.value.replace(/\D/g, '').substring(0, 2))}
                placeholder="MM"
                maxLength={2}
              />
              <Input
                label="Year"
                value={expiryYear}
                onChange={(e) => setExpiryYear(e.target.value.replace(/\D/g, '').substring(0, 4))}
                placeholder="YYYY"
                maxLength={4}
              />
              <Input
                label="CVV"
                type="password"
                value={cvv}
                onChange={(e) => setCvv(e.target.value.replace(/\D/g, '').substring(0, 4))}
                placeholder="***"
                maxLength={4}
              />
            </div>

            <Button type="submit" disabled={loading} className="w-full mt-4">
              {loading ? 'Processing...' : 'Complete Payment'}
            </Button>
          </form>
        </Card>
      </div>

      {/* Test Cards */}
      <div>
        <Card title="Test Cards">
          <p className="text-xs text-slate-500 mb-4">
            Click a card below to auto-fill the form:
          </p>
          <div className="space-y-2">
            {testCards.map((card, index) => (
              <div
                key={index}
                onClick={() => fillCard(card)}
                className={`p-3 rounded-lg cursor-pointer transition-all border ${
                  card.willFail 
                    ? 'bg-red-50 border-red-200 hover:border-red-300' 
                    : 'bg-slate-50 border-slate-200 hover:border-slate-300'
                }`}
              >
                <div className="font-mono text-xs text-slate-700">{card.maskedNumber}</div>
                <div className="flex items-center justify-between mt-2">
                  <span className="text-xs text-slate-500">{card.bankName}</span>
                  {card.willFail ? (
                    <span className="text-red-600 text-xs font-medium">Will Fail</span>
                  ) : (
                    <span className="text-slate-400 text-xs">{card.commission}%</span>
                  )}
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
};

export default PaymentPage;
