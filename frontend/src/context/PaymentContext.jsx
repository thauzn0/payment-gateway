import { createContext, useContext, useState } from 'react';

const PaymentContext = createContext();

export const usePayment = () => {
  const context = useContext(PaymentContext);
  if (!context) {
    throw new Error('usePayment must be used within PaymentProvider');
  }
  return context;
};

export const PaymentProvider = ({ children }) => {
  const [currentPayment, setCurrentPayment] = useState(null);
  const [bankName, setBankName] = useState('');
  const [successData, setSuccessData] = useState(null);

  const startPayment = (paymentData) => {
    setCurrentPayment(paymentData);
  };

  const setBank = (name) => {
    setBankName(name);
  };

  const completePayment = (data) => {
    setSuccessData(data);
  };

  const resetPayment = () => {
    setCurrentPayment(null);
    setBankName('');
    setSuccessData(null);
  };

  return (
    <PaymentContext.Provider
      value={{
        currentPayment,
        bankName,
        successData,
        startPayment,
        setBank,
        completePayment,
        resetPayment,
      }}
    >
      {children}
    </PaymentContext.Provider>
  );
};
