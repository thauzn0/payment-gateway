import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { PaymentProvider } from './context/PaymentContext';
import Navbar from './components/Navbar';
import ShopPage from './pages/ShopPage';
import PaymentPage from './pages/PaymentPage';
import ThreeDSPage from './pages/ThreeDSPage';
import SuccessPage from './pages/SuccessPage';
import DashboardPage from './pages/DashboardPage';
import ApiLogsPage from './pages/ApiLogsPage';

function App() {
  return (
    <PaymentProvider>
      <Router>
        <div className="min-h-screen bg-slate-50">
          <div className="max-w-6xl mx-auto px-6 py-6">
            <Navbar />
            <main>
              <Routes>
                <Route path="/" element={<ShopPage />} />
                <Route path="/payment" element={<PaymentPage />} />
                <Route path="/3ds" element={<ThreeDSPage />} />
                <Route path="/success" element={<SuccessPage />} />
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/logs" element={<ApiLogsPage />} />
              </Routes>
            </main>
          </div>
        </div>
      </Router>
    </PaymentProvider>
  );
}

export default App;
