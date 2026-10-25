import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuthStore } from '@/store/useAuthStore';
import { api } from '@/lib/api';
import { AlertCircle, ArrowRight, Eye, EyeOff, Loader2 } from 'lucide-react';

export function Register() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const { setAuth } = useAuthStore();
  const navigate = useNavigate();

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    try {
      const response = await api.post('/auth/register', { fullName, email, password });
      const { token, user } = response.data;
      setAuth(user, token);
      navigate('/workspace');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create account. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  const inputClass =
    'block w-full h-11 rounded-md border bg-white px-3 text-[15px] text-stone-900 placeholder:text-stone-400 outline-none transition-colors ' +
    'focus:border-teal-800 focus:ring-2 focus:ring-teal-800/15 disabled:bg-stone-100 disabled:text-stone-500 ' +
    (error ? 'border-red-400' : 'border-stone-300 hover:border-stone-400');

  return (
    <div className="min-h-screen grid lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] bg-stone-50 text-stone-900">
      <main className="flex items-center justify-center px-5 py-12 sm:px-10">
        <div className="w-full max-w-[380px]">
          <div className="mb-10 flex items-center gap-2 text-sm font-semibold lg:hidden">
            <span className="grid h-7 w-7 place-items-center rounded bg-teal-950 text-teal-50 text-xs font-bold">W</span>
            Workspace
          </div>

          <h1 className="text-2xl font-semibold tracking-tight">Create an account</h1>
          <p className="mt-1.5 text-sm text-stone-600">Set up your account to access your workspaces.</p>

          {error && (
            <div
              role="alert"
              aria-live="assertive"
              className="mt-6 flex items-start gap-2.5 rounded-md border-l-4 border-red-600 bg-red-50 px-3 py-2.5 text-sm text-red-800"
            >
              <AlertCircle size={16} className="mt-0.5 shrink-0" aria-hidden />
              <span className="break-words">{error}</span>
            </div>
          )}

          <form onSubmit={handleRegister} className="mt-8 space-y-5">
            <div>
              <label htmlFor="register-name" className="mb-1.5 block text-sm font-medium text-stone-800">
                Full name
              </label>
              <input
                id="register-name"
                type="text"
                autoComplete="name"
                autoFocus
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                className={inputClass}
                placeholder="John Doe"
                aria-invalid={!!error}
                disabled={isLoading}
                required
              />
            </div>

            <div>
              <label htmlFor="register-email" className="mb-1.5 block text-sm font-medium text-stone-800">
                Email address
              </label>
              <input
                id="register-email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={inputClass}
                placeholder="you@company.com"
                aria-invalid={!!error}
                disabled={isLoading}
                required
              />
            </div>

            <div>
              <label htmlFor="register-password" className="mb-1.5 block text-sm font-medium text-stone-800">
                Password
              </label>
              <div className="relative">
                <input
                  id="register-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className={inputClass + ' pr-11'}
                  placeholder="Create a password"
                  aria-invalid={!!error}
                  disabled={isLoading}
                  minLength={6}
                  required
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((visible) => !visible)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  aria-pressed={showPassword}
                  className="absolute inset-y-0 right-0 grid w-11 place-items-center rounded-r-md text-stone-500 hover:text-stone-800 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-teal-800/30"
                >
                  {showPassword ? <EyeOff size={17} /> : <Eye size={17} />}
                </button>
              </div>
            </div>

            <button
              type="submit"
              disabled={isLoading}
              className="group flex h-11 w-full items-center justify-center gap-2 rounded-md bg-teal-900 font-medium text-white transition-colors hover:bg-teal-800 active:bg-teal-950 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-teal-800 disabled:cursor-not-allowed disabled:opacity-70"
            >
              {isLoading ? (
                <>
                  <Loader2 size={18} className="animate-spin" aria-hidden />
                  Creating account…
                </>
              ) : (
                <>
                  Create account
                  <ArrowRight size={17} className="transition-transform group-hover:translate-x-0.5" aria-hidden />
                </>
              )}
            </button>
          </form>

          <p className="mt-8 border-t border-stone-200 pt-6 text-sm text-stone-600">
            Already have an account?{' '}
            <Link
              to="/login"
              className="rounded-sm font-medium text-teal-900 underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-teal-800/30"
            >
              Sign in
            </Link>
          </p>
        </div>
      </main>

      <aside className="hidden lg:flex flex-col justify-between bg-teal-950 text-teal-50 px-12 py-10">
        <div className="flex items-center gap-2 text-sm font-semibold tracking-wide">
          <span className="grid h-7 w-7 place-items-center rounded bg-teal-50 text-teal-950 text-xs font-bold">W</span>
          Workspace
        </div>
        <div className="max-w-sm">
          <p className="text-3xl font-semibold leading-tight tracking-tight">
            Bring your team&apos;s work together in one place.
          </p>
          <ul className="mt-8 space-y-3 border-l border-teal-800 pl-4 text-sm text-teal-200">
            <li>Keep your workspaces, files and members in sync.</li>
            <li>Collaborate securely with the right team members.</li>
          </ul>
        </div>
        <p className="text-xs text-teal-400">Create your account and get started with your workspace.</p>
      </aside>
    </div>
  );
}
