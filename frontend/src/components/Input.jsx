const Input = ({
  label,
  type = 'text',
  value,
  onChange,
  placeholder,
  maxLength,
  className = '',
  inputClassName = '',
}) => {
  return (
    <div className={`mb-4 ${className}`}>
      {label && (
        <label className="block text-xs font-medium text-slate-600 mb-1.5">{label}</label>
      )}
      <input
        type={type}
        value={value}
        onChange={onChange}
        placeholder={placeholder}
        maxLength={maxLength}
        className={`w-full px-3 py-2.5 text-sm border border-slate-300 rounded-md focus:border-slate-900 focus:ring-1 focus:ring-slate-900 focus:outline-none transition-colors placeholder:text-slate-400 ${inputClassName}`}
      />
    </div>
  );
};

export default Input;
