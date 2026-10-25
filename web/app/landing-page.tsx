"use client";

import Image from "next/image";
import { useState } from "react";

function BrandLockup() {
  return <a className="brand-lockup" href="#top" aria-label="PritiMitra home">
    <Image className="brand-symbol" src="/brand/image_logo.png" width={39} height={34} alt="" priority />
    <Image className="brand-wordmark" src="/brand/wordmark.png" width={112} height={38} alt="PritiMitra" priority />
  </a>;
}

function PhonePreview({ onLike }: { onLike: () => void }) {
  const [passed, setPassed] = useState(false);
  return <div className="hero-stage" aria-label="Illustration of the PritiMitra app">
    <div className="stage-halo" />
    <span className="orb orb-heart" aria-hidden="true">♥</span>
    <span className="orb orb-orange" aria-hidden="true" />
    <span className="orb orb-lavender" aria-hidden="true" />
    <span className="orb orb-mint" aria-hidden="true" />
    <div className="phone-shell">
      <div className="phone-island" /><div className="phone-app-mark" aria-hidden="true">💗</div>
      <div className="profile-panel"><div className="profile-backdrop" /><div className="profile-avatar"><div className="avatar-head" /><div className="avatar-body" /></div></div>
      <div className="profile-copy"><div className="profile-name">Alex, 27</div><div className="profile-subtitle">Queer Joy · Coffee · Concerts</div><div className="interest-row"><span>Good energy</span><span>Big on kindness</span></div></div>
      <div className="phone-actions">
        <button className="pass-button" type="button" aria-label="Pass on this profile" onClick={() => setPassed(!passed)}>{passed ? "↶" : "×"}</button>
        <button className={passed ? "like-button muted-like" : "like-button"} type="button" aria-label="Like this profile" onClick={onLike}>♥</button>
      </div>
    </div>
    <div className="floating-note connection-note"><div className="note-avatars"><span /><span /></div><div><strong>A connection!</strong><small>starts with hello</small></div></div>
    <div className="floating-note belong-note">You belong here <span>♡</span></div>
  </div>;
}

export default function LandingPage() {
  const [toast, setToast] = useState("");
  const [menuOpen, setMenuOpen] = useState(false);
  function announce(message: string) {
    setToast(message);
    window.setTimeout(() => setToast(""), 3200);
  }

  return <main id="top" className="site-shell">
    <header className="site-header"><div className="header-inner">
      <BrandLockup />
      <button className="mobile-menu-toggle" type="button" aria-expanded={menuOpen} aria-label={menuOpen ? "Close navigation menu" : "Open navigation menu"} onClick={() => setMenuOpen(!menuOpen)}>{menuOpen ? "×" : "☰"}</button>
      <nav className={menuOpen ? "site-nav nav-open" : "site-nav"} aria-label="Main navigation">
        <a href="#the-app" onClick={() => setMenuOpen(false)}>The app</a><a href="#mission" onClick={() => setMenuOpen(false)}>Our mission</a><a href="#safety" onClick={() => setMenuOpen(false)}>Safety &amp; trust</a>
      </nav>
      <button className="button button-primary header-cta" type="button" onClick={() => announce("PritiMitra is getting ready. App access links will be published here.")}>Get the app</button>
    </div></header>
    <section className="hero-section" id="the-app">
      <div className="hero-copy">
        <div className="eyebrow"><span className="eyebrow-dot" />A MORE HUMAN WAY TO MEET</div>
        <h1>Your people.<span>Your kind of connection.</span></h1>
        <p className="hero-description">A playful, safer space to find dating, friendship, and community — without having to pretend to be anyone but yourself.</p>
        <div className="hero-actions">
          <button className="button button-primary" type="button" onClick={() => announce("We’re preparing the PritiMitra app for release. Download links will be added here.")}>Download the app <span aria-hidden="true">↗</span></button>
          <a className="button button-secondary" href="#mission">Explore our mission</a>
        </div>
        <p className="coming-soon">COMING SOON <span>·</span> BUILT FOR LGBTQ+ CONNECTION</p>
        <div className="store-row" aria-label="App store availability">
          <div className="store-card store-dark"><span className="store-icon">A</span><span><small>COMING SOON TO</small><strong>App Store</strong></span></div>
          <div className="store-card"><span className="store-icon play-icon">▶</span><span><small>COMING SOON</small><strong>Google Play</strong></span></div>
        </div>
        <div className="trust-strip" id="safety"><span className="trust-star">✦</span><p>Connection first. Consent always. Your identity, your pace.</p></div>
      </div>
      <PhonePreview onLike={() => announce("Every connection starts with consent and a hello.")} />
    </section>
    <footer className="site-footer" id="mission"><div className="footer-inner">
      <div><h2>A place to meet. A space to belong.</h2><p>PritiMitra Foundation works toward a more inclusive, informed, and supportive world for LGBTQ+ people.</p></div>
      <div className="foundation-link"><a href="https://pritimitra.org" target="_blank" rel="noreferrer">Discover PritiMitra Foundation <span aria-hidden="true">↗</span></a><small>pritimitra.org · Our nonprofit initiative</small></div>
      <small className="copyright">© PritiMitra</small>
    </div></footer>
    <div className={toast ? "toast toast-visible" : "toast"} role="status" aria-live="polite">{toast}</div>
  </main>;
}
