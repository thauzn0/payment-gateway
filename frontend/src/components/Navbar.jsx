import { NavLink } from 'react-router-dom';

const Navbar = () => {
  const linkClass = ({ isActive }) =>
    `px-4 py-2.5 text-sm font-medium transition-all duration-150 border-b-2 ${
      isActive
        ? 'border-slate-900 text-slate-900'
        : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300'
    }`;

  return (
    <nav className="bg-white border-b border-slate-200 mb-8">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1">
          <span className="text-lg font-semibold text-slate-900 mr-8">Payment Gateway</span>
          <NavLink to="/" className={linkClass}>
            Shop
          </NavLink>
          <NavLink to="/dashboard" className={linkClass}>
            Dashboard
          </NavLink>
          <NavLink to="/logs" className={linkClass}>
            API Logs
          </NavLink>
        </div>
        <div className="text-xs text-slate-400">
          Demo Environment
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
