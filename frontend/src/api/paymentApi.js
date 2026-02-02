import axios from 'axios';

const API_BASE = '/api/demo';

const api = axios.create({
  baseURL: API_BASE,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Test kartlarını getir
export const getTestCards = async () => {
  const response = await api.get('/test-cards');
  return response.data;
};

// Sipariş oluştur
export const createOrder = async (orderData) => {
  const response = await api.post('/orders', orderData);
  return response.data;
};

// Kart ile ödeme yap
export const processPayment = async (paymentId, cardData) => {
  const response = await api.post(`/payments/${paymentId}/pay`, cardData);
  return response.data;
};

// 3DS doğrula
export const verify3DS = async (paymentId, otp) => {
  const response = await api.post(`/payments/${paymentId}/verify-3ds`, { otp });
  return response.data;
};

// Tüm ödemeleri getir
export const getPayments = async () => {
  const response = await api.get('/payments');
  return response.data;
};

// Ödeme detayı getir
export const getPaymentDetail = async (paymentId) => {
  const response = await api.get(`/payments/${paymentId}`);
  return response.data;
};

// API loglarını getir
export const getApiLogs = async () => {
  const response = await api.get('/api-logs');
  return response.data;
};

export default api;
