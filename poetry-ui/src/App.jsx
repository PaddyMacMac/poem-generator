import { useState } from "react";
import "./App.css";

const API = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
const GENRES = ["haiku", "playful", "melancholy", "frustrated", "epic"];

const createPoem = async (genre, theme) => {
    const res = await fetch(`${API}/poems`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ genre, theme }),
    });
    if (!res.ok) {
        const { detail } = await res.json().catch(() => ({}));
        throw new Error(detail ?? `Request failed (${res.status})`);
    }
    return res.json();
};

const TopBar = () => (
    <nav className="navbar app-navbar sticky-top">
        <div className="container app-container">
      <span className="navbar-brand d-flex align-items-center gap-2 mb-0 fw-semibold">
        <span className="brand-mark fs-5" aria-hidden="true">🪶</span>
        Poetry Demo
      </span>
            <span className="badge text-bg-light border d-none d-sm-inline">Powered by Gemini</span>
        </div>
    </nav>
);

const Hero = () => (
    <header className="hero">
        <div className="container app-container text-center text-md-start">
            <h1 className="display-5 fw-bold mb-3">Turn any idea into a poem</h1>
            <p className="lead mb-4">
                Pick a style, give it a theme, and let the AI do the rhyming.
            </p>
            <div className="d-flex flex-wrap gap-2 justify-content-center justify-content-md-start">
                {["Spring Boot", "Spring AI", "Gemini", "AWS"].map((tech) => (
                    <span key={tech} className="badge rounded-pill stack-pill px-3 py-2">
            {tech}
          </span>
                ))}
            </div>
        </div>
    </header>
);

const GenreChips = ({ selected, onSelect }) => (
    <div className="mb-4">
        <div className="form-text mb-2">Quick styles</div>
        <div className="d-flex flex-wrap gap-2">
            {GENRES.map((g) => (
                <button
                    key={g}
                    type="button"
                    onClick={() => onSelect(g)}
                    aria-pressed={selected === g}
                    className={`btn btn-sm rounded-pill px-3 genre-chip ${
                        selected === g ? "chip-active" : "btn-outline-secondary"
                    }`}
                >
                    {g}
                </button>
            ))}
        </div>
    </div>
);

const FloatingInput = ({ id, label, value, onChange, maxLength, help }) => (
    <div className="mb-3">
        <div className="form-floating">
            <input
                id={id}
                className="form-control"
                placeholder={label}
                value={value}
                onChange={({ target }) => onChange(target.value)}
                maxLength={maxLength}
                required
            />
            <label htmlFor={id}>{label}</label>
        </div>
        {help && <div className="form-text">{help}</div>}
    </div>
);

const SubmitButton = ({ loading }) => (
    <button className="btn btn-brand btn-lg w-100 py-3 fw-semibold" disabled={loading}>
        {loading ? (
            <>
                <span className="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true" />
                Writing your poem...
            </>
        ) : (
            "Generate poem"
        )}
    </button>
);

const ErrorAlert = ({ message, onClose }) => (
    <div className="alert alert-danger alert-dismissible shadow-sm mt-4" role="alert">
        <strong className="me-1">Something went wrong.</strong>
        {message}
        <button type="button" className="btn-close" aria-label="Close" onClick={onClose} />
    </div>
);

const PoemSkeleton = () => (
    <div className="card border-0 shadow-sm mt-4 poem-skeleton" aria-hidden="true">
        <div className="card-body p-4">
            <p className="placeholder-glow mb-0">
                <span className="placeholder col-5 mb-3 d-block" />
                <span className="placeholder col-9 d-block mb-2" />
                <span className="placeholder col-7 d-block mb-2" />
                <span className="placeholder col-8 d-block" />
            </p>
        </div>
    </div>
);

const PoemCard = ({ title, content, genre, theme }) => {
    const [copied, setCopied] = useState(false);

    const copy = async () => {
        try {
            await navigator.clipboard.writeText(`${title}\n\n${content}`);
            setCopied(true);
            setTimeout(() => setCopied(false), 1800);
        } catch {
            /* clipboard unavailable (e.g. insecure context) - ignore */
        }
    };

    return (
        <article className="card shadow-sm border-0 mt-4 mb-5 poem-card">
            <div className="card-body p-4 p-md-5">
                <div className="d-flex justify-content-between align-items-start gap-3 mb-3">
                    <h2 className="h3 poem-title mb-0">{title}</h2>
                    <button
                        type="button"
                        className="btn btn-sm btn-outline-secondary flex-shrink-0"
                        onClick={copy}
                    >
                        {copied ? "Copied!" : "Copy"}
                    </button>
                </div>
                <p className="poem-text text-break mb-4">{content}</p>
                <div className="d-flex flex-wrap gap-2">
                    <span className="badge rounded-pill text-bg-primary">{genre}</span>
                    <span className="badge rounded-pill text-bg-secondary text-wrap text-start">{theme}</span>
                </div>
            </div>
        </article>
    );
};

const Footer = () => (
    <footer className="app-footer border-top py-4 text-center text-body-secondary small">
        Built with Spring Boot, Spring AI, React and Bootstrap · Hosted on AWS
    </footer>
);

const App = () => {
    const [genre, setGenre] = useState("haiku");
    const [theme, setTheme] = useState("");
    const [poem, setPoem] = useState(null);
    const [error, setError] = useState(null);
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        setPoem(null);
        try {
            setPoem(await createPoem(genre, theme));
        } catch ({ message }) {
            setError(message);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="app-shell bg-body-tertiary">
            <TopBar />
            <Hero />

            <main className="container app-container pull-up">
                <div className="card border-0 generator-card">
                    <form className="card-body p-4 p-md-5" onSubmit={handleSubmit}>
                        <h2 className="h4 fw-semibold mb-4">Write me a poem</h2>
                        <GenreChips selected={genre} onSelect={setGenre} />
                        <FloatingInput
                            id="genre"
                            label="Genre"
                            value={genre}
                            onChange={setGenre}
                            maxLength={50}
                            help="Choose a quick style above or type your own."
                        />
                        <FloatingInput
                            id="theme"
                            label="Theme (e.g. code review comments)"
                            value={theme}
                            onChange={setTheme}
                            maxLength={500}
                        />
                        <SubmitButton loading={loading} />
                    </form>
                </div>

                {error && <ErrorAlert message={error} onClose={() => setError(null)} />}
                {loading && <PoemSkeleton />}
                {poem && <PoemCard {...poem} />}
            </main>

            <Footer />
        </div>
    );
};

export default App;