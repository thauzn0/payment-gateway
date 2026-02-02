const Button = ({ 
  children, 
  onClick, 
  disabled = false, 
  variant = 'primary',
  className = '',
  type = 'button'
}) => {
  const baseClasses = 'px-4 py-2.5 rounded-md text-sm font-medium transition-all duration-150 disabled:opacity-50 disabled:cursor-not-allowed';
  
  const variants = {
    primary: 'bg-slate-900 text-white hover:bg-slate-800',
    success: 'bg-emerald-600 text-white hover:bg-emerald-700',
    secondary: 'bg-white text-slate-700 border border-slate-300 hover:bg-slate-50',
    danger: 'bg-red-600 text-white hover:bg-red-700',
  };

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={`${baseClasses} ${variants[variant]} ${className}`}
    >
      {children}
    </button>
  );
};

export default Button;
