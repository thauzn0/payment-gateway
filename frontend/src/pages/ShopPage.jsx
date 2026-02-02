import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Card from '../components/Card';
import Button from '../components/Button';
import Input from '../components/Input';
import Alert from '../components/Alert';
import { usePayment } from '../context/PaymentContext';
import { createOrder } from '../api/paymentApi';

const ShopPage = () => {
  const navigate = useNavigate();
  const { startPayment } = usePayment();
  const [email, setEmail] = useState('demo@test.com');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const product = {
    name: 'iPhone 15 Pro Max',
    description: '256GB, Titanium Black',
    price: 1500.00,
    sku: 'IPHONE-15-PM-256',
  };

  const handlePurchase = async () => {
    setError('');
    setLoading(true);

    try {
      const order = await createOrder({
        productName: product.name,
        amount: product.price,
        email: email,
      });

      startPayment({
        paymentId: order.paymentId,
        orderId: order.orderId,
        amount: order.amount,
        productName: product.name,
      });

      navigate('/payment');
    } catch (err) {
      setError(err.response?.data?.message || 'Sipariş oluşturulamadı');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card title="Demo Shop">
      <Alert type="error" message={error} show={!!error} />

      {/* Product Card */}
      <div className="flex gap-6 p-4 bg-slate-50 rounded-lg border border-slate-200 mb-6">
        <div className="w-32 h-32 bg-slate-200 rounded-lg flex items-center justify-center">
          <svg className="w-12 h-12 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 18h.01M8 21h8a2 2 0 002-2V5a2 2 0 00-2-2H8a2 2 0 00-2 2v14a2 2 0 002 2z" />
          </svg>
        </div>
        <div className="flex-1">
          <p className="text-xs text-slate-400 font-mono mb-1">{product.sku}</p>
          <h3 className="text-lg font-semibold text-slate-900">{product.name}</h3>
          <p className="text-sm text-slate-500 mt-1">{product.description}</p>
          <p className="text-2xl font-semibold text-slate-900 mt-3">
            {product.price.toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' })}
          </p>
        </div>
      </div>

      {/* Email Input */}
      <Input
        label="E-posta Adresi"
        type="email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        placeholder="ornek@email.com"
      />

      {/* Purchase Button */}
      <Button
        onClick={handlePurchase}
        disabled={loading || !email}
        className="w-full mt-2"
      >
        {loading ? (
          <span className="flex items-center justify-center gap-2">
            <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" fill="none" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
            </svg>
            Processing...
          </span>
        ) : (
          `Checkout - ${product.price.toLocaleString('tr-TR', { style: 'currency', currency: 'TRY' })}`
        )}
      </Button>
    </Card>
  );
};

export default ShopPage;
