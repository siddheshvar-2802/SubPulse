/**
 * SubPulse — Main Dashboard Application Controller
 */

// ── State ───────────────────────────────────────────────────────────────────
let currentSubscriptions = [];
let editingSubscriptionId = null;
let currentAlertSubscriptionId = null;

// ── Toast Notifications ─────────────────────────────────────────────────────
function showToast(message, type = 'info') {
    let container = document.getElementById('toast-container');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toast-container';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    const icon = type === 'success' ? '✅' : type === 'error' ? '❌' : '🔔';
    toast.innerHTML = `<span>${icon}</span><div>${message}</div>`;

    container.appendChild(toast);
    setTimeout(() => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateY(-10px) scale(0.95)';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

// ── PWA & Service Worker ───────────────────────────────────────────────────
let deferredPrompt = null;
window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    deferredPrompt = e;
    const installBtn = document.getElementById('btn-install-pwa');
    if (installBtn) {
        installBtn.style.display = 'inline-flex';
        installBtn.addEventListener('click', async () => {
            if (deferredPrompt) {
                deferredPrompt.prompt();
                const { outcome } = await deferredPrompt.userChoice;
                if (outcome === 'accepted') {
                    showToast('🎉 SubPulse added to home screen!', 'success');
                }
                deferredPrompt = null;
                installBtn.style.display = 'none';
            }
        });
    }
});

if ('serviceWorker' in navigator) {
    window.addEventListener('load', () => {
        navigator.serviceWorker.register('/sw.js').catch(() => {});
    });
}

// ── Init & View Routing ─────────────────────────────────────────────────────
initTheme();

document.addEventListener('DOMContentLoaded', () => {
    initTheme();
    checkAuth();
    setupEventListeners();
});

async function checkAuth() {
    // Check if token was passed via OAuth2 redirect (Google / GitHub)
    const urlParams = new URLSearchParams(window.location.search);
    const oauthToken = urlParams.get('token');
    if (oauthToken) {
        api.setToken(oauthToken);
        window.history.replaceState({}, document.title, window.location.pathname);
        showToast('Logged in with Social Account successfully!', 'success');
    }

    const token = api.getToken();
    const authView = document.getElementById('auth-view');
    const dashboardView = document.getElementById('dashboard-view');

    if (token) {
        authView.style.display = 'none';
        dashboardView.style.display = 'block';

        let user = api.getUser();
        if (user) renderUserProfile(user);

        // Fetch fresh profile from DB
        try {
            const freshUser = await api.getMe();
            if (freshUser) renderUserProfile(freshUser);
        } catch (err) {
            console.warn('Could not refresh profile from /auth/me:', err);
        }

        loadDashboardData();
    } else {
        authView.style.display = 'flex';
        dashboardView.style.display = 'none';
    }
}

function renderUserProfile(user) {
    if (!user) return;
    const displayName = user.fullName || user.email;
    const nameEl = document.getElementById('user-name-display');
    const emailEl = document.getElementById('user-email-display');
    const avatarEl = document.getElementById('user-avatar-initial');

    if (nameEl) nameEl.textContent = displayName;
    if (emailEl) emailEl.textContent = user.email;
    if (avatarEl && displayName) avatarEl.textContent = displayName[0].toUpperCase();
}

// ── Event Listeners Setup ───────────────────────────────────────────────────
function setupEventListeners() {
    // Auth Tabs
    document.querySelectorAll('.auth-tab').forEach(tab => {
        tab.addEventListener('click', (e) => {
            document.querySelectorAll('.auth-tab').forEach(t => t.classList.remove('active'));
            e.target.classList.add('active');
            const mode = e.target.dataset.tab;
            document.getElementById('login-form').style.display = mode === 'login' ? 'block' : 'none';
            document.getElementById('register-form').style.display = mode === 'register' ? 'block' : 'none';
        });
    });

    // Login Submit
    document.getElementById('login-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('login-email').value;
        const pass = document.getElementById('login-password').value;
        try {
            await api.login(email, pass);
            showToast('Logged in successfully!', 'success');
            checkAuth();
        } catch (err) {
            showToast(err.message, 'error');
        }
    });

    // Register Submit
    document.getElementById('register-form')?.addEventListener('submit', async (e) => {
        e.preventDefault();
        const fullName = document.getElementById('reg-fullname').value;
        const email = document.getElementById('reg-email').value;
        const pass = document.getElementById('reg-password').value;
        try {
            await api.register(fullName, email, pass);
            showToast('Registration successful! Welcome to SubPulse.', 'success');
            checkAuth();
        } catch (err) {
            showToast(err.message, 'error');
        }
    });

    // Quick autofill demo button
    document.getElementById('btn-autofill-demo')?.addEventListener('click', () => {
        document.getElementById('login-email').value = 'test.user@subpulse.io';
        document.getElementById('login-password').value = 'Password123!';
    });

    // Logout
    document.getElementById('btn-logout')?.addEventListener('click', async () => {
        const confirmed = await showConfirmDialog({
            title: 'Sign Out?',
            message: 'Are you sure you want to log out of your SubPulse dashboard?',
            confirmText: 'Sign Out',
            cancelText: 'Cancel',
            icon: '🚪',
            isDanger: false
        });
        if (confirmed) {
            api.logout();
        }
    });

    // Currency Switcher
    const currencySwitcher = document.getElementById('currency-switcher');
    if (currencySwitcher) {
        const savedCurrency = localStorage.getItem('subpulse_display_currency') || 'INR';
        currencySwitcher.value = savedCurrency;
        currencySwitcher.addEventListener('change', (e) => {
            localStorage.setItem('subpulse_display_currency', e.target.value);
            showToast(`Display currency changed to ${e.target.value}`, 'info');
            loadDashboardData();
        });
    }

    // Add Subscription Modal triggers
    document.getElementById('btn-add-sub')?.addEventListener('click', () => {
        openSubscriptionModal();
    });

    document.getElementById('sub-modal-close')?.addEventListener('click', () => {
        closeSubscriptionModal();
    });

    document.getElementById('sub-form')?.addEventListener('submit', handleSubscriptionSubmit);

    // Calendar Sync Modal triggers
    document.getElementById('btn-sync-calendar')?.addEventListener('click', openCalendarModal);
    document.getElementById('calendar-modal-close')?.addEventListener('click', closeCalendarModal);
    document.getElementById('btn-copy-cal-feed')?.addEventListener('click', copyCalendarFeedUrl);

    // Monthly Digest Reports triggers
    document.getElementById('btn-export-pdf')?.addEventListener('click', handleExportPdf);
    document.getElementById('btn-email-digest')?.addEventListener('click', handleEmailDigest);

    // Theme Toggle trigger
    document.getElementById('theme-toggle-btn')?.addEventListener('click', toggleTheme);

    // Bank Statement & CSV Auto-Importer triggers
    document.getElementById('btn-open-import')?.addEventListener('click', openImportModal);
    document.getElementById('import-modal-close')?.addEventListener('click', closeImportModal);
    document.getElementById('btn-download-sample-csv')?.addEventListener('click', (e) => {
        e.stopPropagation();
        api.downloadCsvTemplate();
    });

    const dropzone = document.getElementById('import-dropzone');
    const fileInput = document.getElementById('csv-file-input');

    dropzone?.addEventListener('click', () => fileInput?.click());
    dropzone?.addEventListener('dragover', (e) => {
        e.preventDefault();
        dropzone.style.borderColor = 'var(--primary-hover)';
        dropzone.style.background = 'rgba(99, 102, 241, 0.12)';
    });
    dropzone?.addEventListener('dragleave', () => {
        dropzone.style.borderColor = 'rgba(99, 102, 241, 0.4)';
        dropzone.style.background = 'rgba(99, 102, 241, 0.04)';
    });
    dropzone?.addEventListener('drop', (e) => {
        e.preventDefault();
        dropzone.style.borderColor = 'rgba(99, 102, 241, 0.4)';
        dropzone.style.background = 'rgba(99, 102, 241, 0.04)';
        if (e.dataTransfer.files?.length) {
            handleCsvFile(e.dataTransfer.files[0]);
        }
    });

    fileInput?.addEventListener('change', (e) => {
        if (e.target.files?.length) {
            handleCsvFile(e.target.files[0]);
        }
    });

    document.getElementById('btn-toggle-select-all')?.addEventListener('click', toggleSelectAllImport);
    document.getElementById('btn-cancel-import')?.addEventListener('click', resetImportModal);
    document.getElementById('btn-confirm-import')?.addEventListener('click', handleConfirmImport);

    // Alert Config Modal close & channel switch handler
    document.getElementById('alert-modal-close')?.addEventListener('click', () => {
        closeAlertModal();
    });

    const alertChannelSelect = document.getElementById('alert-channel');
    const alertDestInput = document.getElementById('alert-dest');
    const alertDestLabel = document.getElementById('alert-dest-label');
    const telegramHelperBox = document.getElementById('telegram-helper-box');

    alertChannelSelect?.addEventListener('change', () => {
        const val = alertChannelSelect.value;
        if (val === 'TELEGRAM') {
            if (alertDestLabel) alertDestLabel.textContent = 'Telegram Chat ID';
            if (alertDestInput) {
                alertDestInput.placeholder = 'e.g. 123456789 (Your Telegram Chat ID)';
                alertDestInput.required = true;
            }
            if (telegramHelperBox) telegramHelperBox.style.display = 'block';
        } else if (val === 'EMAIL') {
            if (alertDestLabel) alertDestLabel.textContent = 'Destination Email';
            if (alertDestInput) {
                alertDestInput.placeholder = 'e.g. yourname@gmail.com (or leave blank for account email)';
                alertDestInput.required = false;
            }
            if (telegramHelperBox) telegramHelperBox.style.display = 'none';
        } else if (val === 'DISCORD') {
            if (alertDestLabel) alertDestLabel.textContent = 'Discord Webhook URL';
            if (alertDestInput) {
                alertDestInput.placeholder = 'https://discord.com/api/webhooks/...';
                alertDestInput.required = true;
            }
            if (telegramHelperBox) telegramHelperBox.style.display = 'none';
        } else if (val === 'WEBHOOK') {
            if (alertDestLabel) alertDestLabel.textContent = 'HTTP POST Webhook URL';
            if (alertDestInput) {
                alertDestInput.placeholder = 'https://your-api.com/webhook/subpulse';
                alertDestInput.required = true;
            }
            if (telegramHelperBox) telegramHelperBox.style.display = 'none';
        }
    });

    document.getElementById('alert-form')?.addEventListener('submit', handleAlertSubmit);

    // Cancel Guide Modal Close
    document.getElementById('cancel-modal-close')?.addEventListener('click', closeCancelModal);

    // Bill Splitter Modal Handlers
    document.getElementById('btn-open-splitter')?.addEventListener('click', () => openSplitterModal());
    document.getElementById('splitter-modal-close')?.addEventListener('click', closeSplitterModal);
    document.getElementById('splitter-sub-select')?.addEventListener('change', (e) => {
        const opt = e.target.selectedOptions[0];
        if (opt) {
            const amt = opt.getAttribute('data-amount');
            if (amt) document.getElementById('splitter-total-amount').value = amt;
            recalculateSplit();
        }
    });
    document.getElementById('splitter-total-amount')?.addEventListener('input', recalculateSplit);
    document.getElementById('splitter-members-count')?.addEventListener('input', recalculateSplit);

    document.getElementById('btn-copy-reminder')?.addEventListener('click', () => {
        const text = document.getElementById('splitter-reminder-text')?.value;
        if (text) {
            navigator.clipboard.writeText(text);
            showToast('📋 Payment reminder copied to clipboard!', 'success');
        }
    });

    document.getElementById('btn-share-telegram')?.addEventListener('click', () => {
        const text = document.getElementById('splitter-reminder-text')?.value;
        if (text) {
            window.open(`https://t.me/share/url?url=${encodeURIComponent(text)}`, '_blank');
        }
    });
}

// ── Load Dashboard Data ─────────────────────────────────────────────────────
async function loadDashboardData() {
    try {
        const selectedCurrency = document.getElementById('currency-switcher')?.value || 
                                 localStorage.getItem('subpulse_display_currency') || 'INR';

        const [subs, analytics, upcoming, aiOpt] = await Promise.all([
            api.getSubscriptions(),
            api.getAnalytics(selectedCurrency),
            api.getUpcoming(30),
            api.getAiOptimization(selectedCurrency)
        ]);

        currentSubscriptions = subs || [];

        renderStats(analytics);
        renderAiOptimization(aiOpt);
        renderUpcoming(upcoming);
        renderSubscriptionsTable(currentSubscriptions);
        charts.renderForecastChart('forecast-chart-canvas', currentSubscriptions, selectedCurrency);
        charts.renderCategoryDonutChart('category-donut-canvas', analytics?.spendByCategory, selectedCurrency);
        charts.renderCategoryBars('category-bars-container', analytics?.spendByCategory, selectedCurrency);
        populateSplitterDropdown(currentSubscriptions);

    } catch (err) {
        showToast('Error loading dashboard: ' + err.message, 'error');
    }
}

// ── Render AI Optimization Insights ─────────────────────────────────────────
function renderAiOptimization(aiOpt) {
    const section = document.getElementById('ai-insights-section');
    const container = document.getElementById('ai-recommendations-container');
    const scoreEl = document.getElementById('ai-health-score');
    const savingsEl = document.getElementById('ai-total-savings');

    if (!aiOpt || !container) return;

    // Score & Color
    const score = aiOpt.healthScore || 100;
    const scoreColor = score >= 85 ? 'var(--accent-emerald)' : score >= 70 ? 'var(--accent-cyan)' : score >= 50 ? 'var(--accent-amber)' : 'var(--accent-rose)';
    if (scoreEl) {
        scoreEl.textContent = `${score} / 100`;
        scoreEl.style.color = scoreColor;
    }

    if (savingsEl) {
        savingsEl.textContent = `${aiOpt.currency} ${Number(aiOpt.totalPotentialAnnualSavings || 0).toFixed(2)}/yr`;
    }

    const recs = aiOpt.recommendations || [];
    if (recs.length === 0) {
        container.innerHTML = `
            <div style="grid-column: 1 / -1; padding: 14px 18px; background: rgba(16, 185, 129, 0.08); border: 1px solid rgba(16, 185, 129, 0.2); border-radius: 10px; display: flex; align-items: center; gap: 12px;">
                <span style="font-size: 20px;">✨</span>
                <div>
                    <div style="font-weight: 700; color: #34d399; font-size: 13.5px;">Your Subscriptions are Fully Optimized!</div>
                    <div style="font-size: 12px; color: var(--text-muted);">No overlapping services, expensive unmonitored monthly plans, or risky trials detected.</div>
                </div>
            </div>
        `;
        return;
    }

    let html = '';
    recs.forEach(rec => {
        const typeBadgeClass = rec.type === 'DUPLICATE_SERVICE' ? 'badge-warning' : rec.type === 'EXPIRING_TRIAL' ? 'badge-urgent' : 'badge-ok';
        const typeLabel = rec.type === 'DUPLICATE_SERVICE' ? '⚠️ Overlap Detected' : rec.type === 'EXPIRING_TRIAL' ? '🚨 Trial Ending' : '💡 Annual Arbitrage';
        const savingsText = Number(rec.potentialAnnualSavings || 0) > 0 ? `Save ${rec.currency} ${Number(rec.potentialAnnualSavings).toFixed(2)}/yr` : 'Review Plan';

        html += `
            <div class="ai-card-item" style="background: rgba(15, 23, 42, 0.85); border: 1px solid var(--border-subtle); border-radius: 12px; padding: 16px; display: flex; flex-direction: column; justify-content: space-between; transition: var(--transition);">
                <div>
                    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                        <span class="badge ${typeBadgeClass}" style="font-size: 11px;">${typeLabel}</span>
                        <span style="font-weight: 800; font-size: 12.5px; color: #34d399;">${savingsText}</span>
                    </div>
                    <div style="font-weight: 700; font-size: 13.5px; color: var(--text-primary); margin-bottom: 4px;">${rec.title}</div>
                    <div style="font-size: 12px; color: var(--text-muted); line-height: 1.4;">${rec.description}</div>
                </div>
                <div style="margin-top: 14px;">
                    ${rec.actionUrl ? `
                        <a href="${rec.actionUrl}" target="_blank" class="btn btn-secondary btn-sm" style="width: 100%; text-align: center; text-decoration: none; display: block; font-size: 12px;">
                            ${rec.actionLabel || 'Manage Plan'} ↗
                        </a>
                    ` : `
                        <a href="#dashboard-table-card" onclick="document.getElementById('search-input')?.focus()" class="btn btn-secondary btn-sm" style="width: 100%; text-align: center; text-decoration: none; display: block; font-size: 12px;">
                            Review in Table 📋
                        </a>
                    `}
                </div>
            </div>
        `;
    });

    container.innerHTML = html;
}

// ── Render Stats Ribbon ─────────────────────────────────────────────────────
function renderStats(analytics) {
    if (!analytics) return;
    const cur = analytics.currency || 'USD';

    document.getElementById('stat-monthly-spend').textContent = `${cur} ${Number(analytics.monthlySpend || 0).toFixed(2)}`;
    document.getElementById('stat-annual-spend').textContent = `${cur} ${Number(analytics.annualSpend || 0).toFixed(2)}`;
    document.getElementById('stat-active-count').textContent = analytics.totalActiveSubscriptions || 0;
    document.getElementById('stat-renewing-7d').textContent = analytics.renewingInNextSevenDays || 0;
}

// ── Render Upcoming Renewals ────────────────────────────────────────────────
function renderUpcoming(upcoming) {
    const container = document.getElementById('upcoming-list-container');
    if (!container) return;

    if (!upcoming || upcoming.length === 0) {
        container.innerHTML = `<p style="color: var(--text-muted); font-size: 13px; text-align: center; padding: 15px 0;">No upcoming renewals in next 30 days.</p>`;
        return;
    }

    const today = new Date();
    today.setHours(0, 0, 0, 0);

    let html = '';
    upcoming.slice(0, 5).forEach(sub => {
        const billDate = new Date(sub.nextBillingDate);
        const diffDays = Math.ceil((billDate - today) / (1000 * 60 * 60 * 24));
        const badgeClass = diffDays <= 3 ? 'badge-urgent' : diffDays <= 7 ? 'badge-warning' : 'badge-ok';
        const label = diffDays === 0 ? 'Today!' : diffDays === 1 ? 'Tomorrow' : `in ${diffDays}d`;

        const uLogo = getServiceLogoUrl(sub.serviceName, sub.websiteUrl);
        html += `
            <div class="upcoming-item">
                <div class="upcoming-item-content">
                    <div style="width: 30px; height: 30px; border-radius: 6px; background: rgba(255, 255, 255, 0.05); border: 1px solid var(--border-subtle); display: flex; align-items: center; justify-content: center; flex-shrink: 0;">
                        ${uLogo ? `<img src="${uLogo}" alt="${sub.serviceName}" style="width: 18px; height: 18px; object-fit: contain;" />` : `<span>⚡</span>`}
                    </div>
                    <div class="upcoming-item-text">
                        <div class="upcoming-item-title">${escapeHtml(sub.serviceName)}</div>
                        <div style="font-size: 11px; color: var(--text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis;">${sub.currency} ${Number(sub.amount).toFixed(2)} / ${sub.billingCycle.toLowerCase()}</div>
                    </div>
                </div>
                <span class="badge ${badgeClass}" style="flex-shrink: 0;">${label}</span>
            </div>
        `;
    });

    container.innerHTML = html;
}

// ── Render Subscriptions Table ──────────────────────────────────────────────
function renderSubscriptionsTable(subscriptions) {
    const tbody = document.getElementById('subs-table-body');
    const emptyState = document.getElementById('subs-empty-state');
    if (!tbody) return;

    if (!subscriptions || subscriptions.length === 0) {
        tbody.innerHTML = '';
        if (emptyState) emptyState.style.display = 'block';
        return;
    }

    if (emptyState) emptyState.style.display = 'none';

    // Sort: Active first (A-Z), Stopped/Paused last (A-Z)
    const sortedSubscriptions = [...subscriptions].sort((a, b) => {
        if (a.isActive !== b.isActive) {
            return a.isActive ? -1 : 1;
        }
        return (a.serviceName || '').localeCompare(b.serviceName || '', undefined, { sensitivity: 'base' });
    });

    let html = '';
    sortedSubscriptions.forEach(sub => {
        const initial = sub.serviceName ? sub.serviceName[0].toUpperCase() : 'S';
        const color = charts.getColor(sub.category);
        const logoUrl = getServiceLogoUrl(sub.serviceName, sub.websiteUrl);

        html += `
            <tr style="${!sub.isActive ? 'opacity: 0.55; filter: grayscale(20%);' : ''}">
                <td>
                    <div class="service-cell">
                        <div class="service-icon" style="background: rgba(255, 255, 255, 0.04); border-color: ${color}33;">
                            ${logoUrl ? `
                                <img src="${logoUrl}" alt="${sub.serviceName}" style="width: 22px; height: 22px; object-fit: contain; border-radius: 4px;" onerror="this.style.display='none'; this.nextElementSibling.style.display='block';" />
                                <span style="display: none; color: ${color};">${initial}</span>
                            ` : `
                                <span style="color: ${color};">${initial}</span>
                            `}
                        </div>
                        <div>
                            <div class="service-name">${escapeHtml(sub.serviceName)}</div>
                            <div class="service-desc">${escapeHtml(sub.description || 'No description')}</div>
                        </div>
                    </div>
                </td>
                <td>
                    <span class="badge badge-category">${sub.category.replace('_', ' ')}</span>
                </td>
                <td>
                    <div style="font-weight: 700; color: var(--text-primary);">${sub.currency} ${Number(sub.amount).toFixed(2)}</div>
                    <span class="badge badge-cycle">${sub.billingCycle}</span>
                </td>
                <td style="width: 105px; white-space: nowrap;">
                    <div style="line-height: 1.25;">
                        <div style="font-weight: 700; color: var(--text-primary); font-size: 12.5px;">${formatCompactDate(sub.nextBillingDate)}</div>
                        <div style="font-size: 11px; color: var(--text-muted);">${formatRelativeDays(sub.nextBillingDate)}</div>
                    </div>
                </td>
                <td>
                    <label class="toggle-switch">
                        <input type="checkbox" ${sub.isActive ? 'checked' : ''} onchange="toggleSubscriptionStatus(${sub.id})">
                        <span class="slider"></span>
                    </label>
                </td>
                <td>
                    <div style="display: flex; gap: 6px; align-items: center;">
                        <button class="btn btn-secondary btn-sm" onclick="openAlertModal(${sub.id}, '${escapeHtml(sub.serviceName)}')" title="Configure renewal alerts" aria-label="Alerts" style="padding: 6px 9px;">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--accent-amber);">
                                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                                <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                            </svg>
                        </button>
                        <button class="btn btn-secondary btn-sm" onclick="openCancelModal('${escapeHtml(sub.serviceName)}')" title="How to cancel subscription" aria-label="Cancel Guide" style="padding: 6px 9px;">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--accent-rose);">
                                <circle cx="12" cy="12" r="10"></circle>
                                <line x1="4.93" y1="4.93" x2="19.07" y2="19.07"></line>
                            </svg>
                        </button>
                        <button class="btn btn-secondary btn-sm" onclick="openSplitterModal(${sub.id})" title="Split bill with family/team" aria-label="Split Bill" style="padding: 6px 9px;">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--primary);">
                                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                                <circle cx="9" cy="7" r="4"></circle>
                                <path d="M23 21v-2a4 4 0 0 0-3-3.87"></path>
                                <path d="M16 3.13a4 4 0 0 1 0 7.75"></path>
                            </svg>
                        </button>
                        <button class="btn btn-secondary btn-sm" onclick="editSubscription(${sub.id})" title="Edit subscription" aria-label="Edit" style="padding: 6px 9px;">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                            </svg>
                        </button>
                        <button class="btn btn-outline-danger btn-sm" onclick="deleteSubscription(${sub.id})" title="Delete subscription" aria-label="Delete" style="padding: 6px 9px;">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <polyline points="3 6 5 6 21 6"></polyline>
                                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                                <line x1="10" y1="11" x2="10" y2="17"></line>
                                <line x1="14" y1="11" x2="14" y2="17"></line>
                            </svg>
                        </button>
                    </div>
                </td>
            </tr>
        `;
    });

    tbody.innerHTML = html;
}

// ── Subscription CRUD Actions ───────────────────────────────────────────────
function openSubscriptionModal(sub = null) {
    editingSubscriptionId = sub ? sub.id : null;
    const modal = document.getElementById('sub-modal');
    document.getElementById('sub-modal-title').textContent = sub ? 'Edit Subscription' : 'Add New Subscription';

    if (sub) {
        document.getElementById('sub-name').value = sub.serviceName;
        document.getElementById('sub-desc').value = sub.description || '';
        document.getElementById('sub-url').value = sub.websiteUrl || '';
        document.getElementById('sub-amount').value = sub.amount;
        document.getElementById('sub-currency').value = sub.currency;
        document.getElementById('sub-cycle').value = sub.billingCycle;
        document.getElementById('sub-start-date').value = sub.startDate;
        document.getElementById('sub-billing-date').value = sub.nextBillingDate;
        document.getElementById('sub-category').value = sub.category;
    } else {
        document.getElementById('sub-form').reset();
        const today = new Date().toISOString().split('T')[0];
        document.getElementById('sub-start-date').value = today;
        const nextMonth = new Date();
        nextMonth.setMonth(nextMonth.getMonth() + 1);
        document.getElementById('sub-billing-date').value = nextMonth.toISOString().split('T')[0];
    }

    modal.classList.add('active');
}

function closeSubscriptionModal() {
    document.getElementById('sub-modal').classList.remove('active');
    editingSubscriptionId = null;
}

async function handleSubscriptionSubmit(e) {
    e.preventDefault();
    const payload = {
        serviceName: document.getElementById('sub-name').value,
        description: document.getElementById('sub-desc').value,
        websiteUrl: document.getElementById('sub-url').value,
        amount: parseFloat(document.getElementById('sub-amount').value),
        currency: document.getElementById('sub-currency').value,
        billingCycle: document.getElementById('sub-cycle').value,
        startDate: document.getElementById('sub-start-date').value,
        nextBillingDate: document.getElementById('sub-billing-date').value,
        category: document.getElementById('sub-category').value,
        autoRenew: true
    };

    try {
        if (editingSubscriptionId) {
            await api.updateSubscription(editingSubscriptionId, payload);
            showToast('Subscription updated successfully!', 'success');
        } else {
            await api.createSubscription(payload);
            showToast('New subscription added!', 'success');
        }
        closeSubscriptionModal();
        loadDashboardData();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function toggleSubscriptionStatus(id) {
    try {
        const updated = await api.toggleSubscription(id);
        showToast(`Subscription '${updated.serviceName}' is now ${updated.isActive ? 'Active' : 'Paused'}.`, 'info');
        loadDashboardData();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

function editSubscription(id) {
    const sub = currentSubscriptions.find(s => s.id === id);
    if (sub) openSubscriptionModal(sub);
}

// ── Reusable Centered Confirmation Dialog ──────────────────────────────────
function showConfirmDialog({ title = 'Are you sure?', message = '', confirmText = 'Confirm', cancelText = 'Cancel', iconType = 'trash', isDanger = true }) {
    return new Promise((resolve) => {
        const modal = document.getElementById('confirm-modal');
        if (!modal) {
            resolve(confirm(message || title));
            return;
        }

        const titleEl = document.getElementById('confirm-modal-title');
        const msgEl = document.getElementById('confirm-modal-message');
        const iconEl = document.getElementById('confirm-modal-icon');
        const okBtn = document.getElementById('confirm-modal-ok-btn');
        const cancelBtn = document.getElementById('confirm-modal-cancel-btn');

        if (titleEl) titleEl.textContent = title;
        if (msgEl) msgEl.textContent = message;
        if (cancelBtn) cancelBtn.textContent = cancelText;

        const iconSvgMap = {
            'trash': `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--accent-rose, #f43f5e);"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path><line x1="10" y1="11" x2="10" y2="17"></line><line x1="14" y1="11" x2="14" y2="17"></line></svg>`,
            'logout': `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--primary, #6366f1);"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"></path><polyline points="16 17 21 12 16 7"></polyline><line x1="21" y1="12" x2="9" y2="12"></line></svg>`,
            'warning': `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--accent-amber, #f59e0b);"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>`
        };

        if (iconEl) {
            iconEl.innerHTML = iconSvgMap[iconType] || iconSvgMap['trash'];
            if (isDanger) {
                iconEl.style.background = 'rgba(244, 63, 94, 0.12)';
                iconEl.style.borderColor = 'rgba(244, 63, 94, 0.25)';
            } else {
                iconEl.style.background = 'rgba(99, 102, 241, 0.12)';
                iconEl.style.borderColor = 'rgba(99, 102, 241, 0.25)';
            }
        }

        if (okBtn) {
            okBtn.textContent = confirmText;
            if (isDanger) {
                okBtn.style.background = 'var(--accent-rose, #f43f5e)';
            } else {
                okBtn.style.background = 'var(--primary, #6366f1)';
            }
        }

        modal.classList.add('active');

        function cleanup(result) {
            modal.classList.remove('active');
            okBtn?.removeEventListener('click', onOk);
            cancelBtn?.removeEventListener('click', onCancel);
            modal.removeEventListener('click', onBackdrop);
            resolve(result);
        }

        function onOk() { cleanup(true); }
        function onCancel() { cleanup(false); }
        function onBackdrop(e) {
            if (e.target === modal) cleanup(false);
        }

        okBtn?.addEventListener('click', onOk);
        cancelBtn?.addEventListener('click', onCancel);
        modal.addEventListener('click', onBackdrop);
    });
}

async function deleteSubscription(id) {
    const sub = currentSubscriptions.find(s => s.id === id);
    const serviceName = sub ? sub.serviceName : 'this subscription';
    
    const confirmed = await showConfirmDialog({
        title: `Delete "${serviceName}"?`,
        message: 'This will remove the subscription and all associated renewal alerts. This action cannot be undone.',
        confirmText: 'Yes, Delete',
        cancelText: 'Cancel',
        iconType: 'trash',
        isDanger: true
    });

    if (!confirmed) return;

    try {
        await api.deleteSubscription(id);
        showToast(`Subscription "${serviceName}" deleted successfully.`, 'success');
        loadDashboardData();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function openAlertModal(subscriptionId, serviceName) {
    currentAlertSubscriptionId = subscriptionId;
    document.getElementById('alert-modal-title').textContent = `Alert Rules: ${serviceName}`;

    // Reset default to Telegram Bot
    const channelSelect = document.getElementById('alert-channel');
    if (channelSelect) channelSelect.value = 'TELEGRAM';
    const destLabel = document.getElementById('alert-dest-label');
    if (destLabel) destLabel.textContent = 'Telegram Chat ID';
    const destInput = document.getElementById('alert-dest');
    if (destInput) {
        destInput.value = '';
        destInput.placeholder = 'e.g. 123456789 (Your Telegram Chat ID)';
        destInput.required = true;
    }
    const helperBox = document.getElementById('telegram-helper-box');
    if (helperBox) helperBox.style.display = 'block';

    document.getElementById('alert-modal').classList.add('active');
    loadAlertRules(subscriptionId);
}

function closeAlertModal() {
    document.getElementById('alert-modal').classList.remove('active');
    currentAlertSubscriptionId = null;
}

async function loadAlertRules(subscriptionId) {
    const container = document.getElementById('alert-rules-list');
    container.innerHTML = '<p style="color: var(--text-muted); font-size: 13px;">Loading rules...</p>';

    try {
        const rules = await api.getAlerts(subscriptionId);
        if (!rules || rules.length === 0) {
            container.innerHTML = '<p style="color: var(--text-muted); font-size: 13px;">No alert rules configured yet.</p>';
            return;
        }

        let html = '';
        rules.forEach(r => {
            const channelIcon = r.channel === 'TELEGRAM' ? '✈️ Telegram' :
                                r.channel === 'EMAIL' ? '📧 Email' :
                                r.channel === 'DISCORD' ? '🎮 Discord' : '🔗 Webhook';

            html += `
                <div style="display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; background: rgba(255,255,255,0.03); border: 1px solid var(--border-subtle); border-radius: 8px; margin-bottom: 8px;">
                    <div>
                        <div style="font-weight: 600; font-size: 13px;">🔔 Notify ${r.daysBefore} day(s) before</div>
                        <div style="font-size: 11.5px; color: var(--text-muted); margin-top: 2px;">via <strong style="color: var(--text-primary);">${channelIcon}</strong> (${r.destination || 'default email'})</div>
                    </div>
                    <button class="btn btn-outline-danger btn-sm" onclick="removeAlertRule(${r.id})">Delete</button>
                </div>
            `;
        });
        container.innerHTML = html;
    } catch (err) {
        container.innerHTML = `<p style="color: var(--accent-rose); font-size: 13px;">${err.message}</p>`;
    }
}

async function handleAlertSubmit(e) {
    e.preventDefault();
    if (!currentAlertSubscriptionId) return;

    const payload = {
        daysBefore: parseInt(document.getElementById('alert-days').value),
        channel: document.getElementById('alert-channel').value,
        destination: document.getElementById('alert-dest').value || null,
        isEnabled: true
    };

    try {
        await api.createAlert(currentAlertSubscriptionId, payload);
        showToast('Alert rule added!', 'success');
        document.getElementById('alert-dest').value = '';
        loadAlertRules(currentAlertSubscriptionId);
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function removeAlertRule(alertId) {
    if (!currentAlertSubscriptionId) return;
    const confirmed = await showConfirmDialog({
        title: 'Delete Alert Rule?',
        message: 'Are you sure you want to delete this renewal alert notification rule?',
        confirmText: 'Yes, Delete',
        cancelText: 'Cancel',
        iconType: 'trash',
        isDanger: true
    });
    if (!confirmed) return;
    try {
        await api.deleteAlert(currentAlertSubscriptionId, alertId);
        showToast('Alert rule deleted.', 'info');
        loadAlertRules(currentAlertSubscriptionId);
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ── Manual Alert Ping / Instant Notification Trigger ─────────────────────────
async function triggerInstantTestAlert(subscriptionId, serviceName) {
    showToast(`⚡ Dispatching instant test alert for "${serviceName}"...`, 'info');
    try {
        await api.triggerTestAlert(subscriptionId, 7);
        showToast(`⚡ Instant alert sent for "${serviceName}"! Check your Telegram / Email.`, 'success');
    } catch (err) {
        showToast('Alert dispatch notice: ' + err.message, 'error');
    }
}

// ── Calendar Sync Actions ───────────────────────────────────────────────────
async function openCalendarModal() {
    const modal = document.getElementById('calendar-modal');
    modal.classList.add('active');

    try {
        const info = await api.getCalendarSyncInfo();
        document.getElementById('btn-google-cal-link').href = info.googleCalendarUrl;
        document.getElementById('btn-apple-cal-link').href = info.webcalFeedUrl;
        document.getElementById('btn-download-ics-link').href = info.httpFeedUrl;
        document.getElementById('calendar-feed-url-input').value = info.httpFeedUrl;
    } catch (err) {
        showToast('Could not load calendar sync links: ' + err.message, 'error');
    }
}

function closeCalendarModal() {
    document.getElementById('calendar-modal')?.classList.remove('active');
}

function copyCalendarFeedUrl() {
    const input = document.getElementById('calendar-feed-url-input');
    if (!input || !input.value) return;
    navigator.clipboard.writeText(input.value).then(() => {
        showToast('📋 Calendar feed URL copied to clipboard!', 'success');
    }).catch(() => {
        input.select();
        document.execCommand('copy');
        showToast('📋 Calendar feed URL copied!', 'success');
    });
}

// ── Monthly Executive Digest Actions ─────────────────────────────────────────
async function handleExportPdf() {
    const selectedCurrency = document.getElementById('currency-switcher')?.value || 
                             localStorage.getItem('subpulse_display_currency') || 'USD';
    showToast('📊 Generating your Executive Monthly PDF Report...', 'info');
    try {
        await api.downloadMonthlyPdf(selectedCurrency);
        showToast('✅ Monthly PDF Report downloaded successfully!', 'success');
    } catch (err) {
        showToast(err.message, 'error');
    }
}

async function handleEmailDigest() {
    const selectedCurrency = document.getElementById('currency-switcher')?.value || 
                             localStorage.getItem('subpulse_display_currency') || 'USD';
    showToast('📧 Compiling and sending Monthly Executive Digest email...', 'info');
    try {
        await api.sendMonthlyEmailDigest(selectedCurrency);
        showToast('✅ Monthly digest & PDF report dispatched to your inbox!', 'success');
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ── Bank Statement & CSV Auto-Importer Actions ──────────────────────────────
let detectedImportList = [];

function openImportModal() {
    resetImportModal();
    document.getElementById('import-modal')?.classList.add('active');
}

function closeImportModal() {
    document.getElementById('import-modal')?.classList.remove('active');
}

function resetImportModal() {
    detectedImportList = [];
    const dropzone = document.getElementById('import-dropzone');
    const loading = document.getElementById('import-loading');
    const preview = document.getElementById('import-preview-section');
    const fileInput = document.getElementById('csv-file-input');

    if (dropzone) dropzone.style.display = 'block';
    if (loading) loading.style.display = 'none';
    if (preview) preview.style.display = 'none';
    if (fileInput) fileInput.value = '';
}

async function handleCsvFile(file) {
    if (!file) return;

    const dropzone = document.getElementById('import-dropzone');
    const loading = document.getElementById('import-loading');
    const preview = document.getElementById('import-preview-section');

    dropzone.style.display = 'none';
    loading.style.display = 'block';
    preview.style.display = 'none';

    try {
        const detected = await api.previewCsvImport(file);
        detectedImportList = detected || [];

        loading.style.display = 'none';

        if (detectedImportList.length === 0) {
            showToast('No recognizable recurring subscriptions found in this statement.', 'warning');
            resetImportModal();
            return;
        }

        renderImportPreviewTable(detectedImportList);
        preview.style.display = 'block';
        showToast(`⚡ Detected ${detectedImportList.length} recurring subscriptions!`, 'success');
    } catch (err) {
        loading.style.display = 'none';
        dropzone.style.display = 'block';
        showToast('CSV parse error: ' + err.message, 'error');
    }
}

function renderImportPreviewTable(items) {
    const tbody = document.getElementById('import-preview-tbody');
    const countEl = document.getElementById('detected-count');
    if (countEl) countEl.textContent = items.length;

    const categoryOptions = [
        'AI_TOOLS', 'ENTERTAINMENT', 'DEVELOPER_TOOLS', 'PRODUCTIVITY',
        'DESIGN', 'CLOUD_STORAGE', 'SECURITY', 'COMMUNICATION', 'FINANCE', 'OTHER'
    ];

    const cycleOptions = ['MONTHLY', 'ANNUAL', 'QUARTERLY', 'WEEKLY'];

    tbody.innerHTML = items.map((item, idx) => {
        const isChecked = !item.alreadyTracked;
        const trackedBadge = item.alreadyTracked 
            ? `<span style="font-size: 10px; color: var(--accent-amber); margin-left: 6px;">(Already Tracked)</span>` 
            : '';

        return `
            <tr id="row-${item.tempId}" style="${item.alreadyTracked ? 'opacity: 0.65;' : ''}">
                <td style="text-align: center;">
                    <input type="checkbox" class="import-checkbox" data-temp-id="${item.tempId}" ${isChecked ? 'checked' : ''} style="cursor: pointer; width: 16px; height: 16px; accent-color: var(--primary-color);" />
                </td>
                <td>
                    <div style="font-weight: 700; color: var(--text-primary);">${escapeHtml(item.serviceName)}${trackedBadge}</div>
                    <div style="font-size: 11px; color: var(--text-muted); font-family: monospace; max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">${escapeHtml(item.rawDescription || '')}</div>
                </td>
                <td>
                    <select id="cat-${item.tempId}" class="form-select btn-sm" style="font-size: 11.5px; padding: 4px 6px; background: var(--bg-input); border-color: var(--border-subtle); color: var(--text-primary);">
                        ${categoryOptions.map(c => `<option value="${c}" ${c === item.category ? 'selected' : ''}>${formatCategory(c)}</option>`).join('')}
                    </select>
                </td>
                <td>
                    <select id="cycle-${item.tempId}" class="form-select btn-sm" style="font-size: 11.5px; padding: 4px 6px; background: var(--bg-input); border-color: var(--border-subtle); color: var(--text-primary);">
                        ${cycleOptions.map(cy => `<option value="${cy}" ${cy === item.billingCycle ? 'selected' : ''}>${cy}</option>`).join('')}
                    </select>
                </td>
                <td>
                    <div style="display: flex; align-items: center; gap: 4px;">
                        <span style="font-size: 11px; color: var(--text-muted);">${item.currency || 'USD'}</span>
                        <input id="amt-${item.tempId}" type="number" step="0.01" value="${item.amount}" style="width: 70px; font-size: 12px; font-weight: 700; background: var(--bg-input); border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--text-primary); padding: 4px;" />
                    </div>
                </td>
                <td>
                    <input id="date-${item.tempId}" type="date" value="${item.nextBillingDate || ''}" style="font-size: 11.5px; background: var(--bg-input); border: 1px solid var(--border-subtle); border-radius: 4px; color: var(--text-primary); padding: 4px;" />
                </td>
            </tr>
        `;
    }).join('');
}

function toggleSelectAllImport() {
    const checkboxes = document.querySelectorAll('.import-checkbox');
    const anyUnchecked = Array.from(checkboxes).some(cb => !cb.checked);
    checkboxes.forEach(cb => cb.checked = anyUnchecked);
}

async function handleConfirmImport() {
    const checkboxes = document.querySelectorAll('.import-checkbox:checked');
    if (checkboxes.length === 0) {
        showToast('Please select at least one subscription to import.', 'warning');
        return;
    }

    const payloadList = [];

    checkboxes.forEach(cb => {
        const tempId = cb.dataset.tempId;
        const orig = detectedImportList.find(d => d.tempId === tempId);
        if (!orig) return;

        const catEl = document.getElementById(`cat-${tempId}`);
        const cycleEl = document.getElementById(`cycle-${tempId}`);
        const amtEl = document.getElementById(`amt-${tempId}`);
        const dateEl = document.getElementById(`date-${tempId}`);

        payloadList.push({
            serviceName: orig.serviceName,
            amount: parseFloat(amtEl?.value || orig.amount),
            currency: orig.currency || 'USD',
            category: catEl?.value || orig.category || 'OTHER',
            billingCycle: cycleEl?.value || orig.billingCycle || 'MONTHLY',
            startDate: orig.transactionDate || new Date().toISOString().slice(0, 10),
            nextBillingDate: dateEl?.value || orig.nextBillingDate || new Date().toISOString().slice(0, 10),
            websiteUrl: orig.websiteUrl || null,
            description: `Auto-imported from statement line: ${orig.rawDescription || orig.serviceName}`
        });
    });

    showToast(`⚡ Importing ${payloadList.length} subscriptions...`, 'info');

    try {
        await api.confirmCsvImport(payloadList);
        showToast(`🎉 Successfully imported ${payloadList.length} subscriptions!`, 'success');
        closeImportModal();
        loadDashboardData();
    } catch (err) {
        showToast('Import failed: ' + err.message, 'error');
    }
}

// ── Compact Date Formatting Helpers ─────────────────────────────────────────
function formatCompactDate(dateStr) {
    if (!dateStr) return '--';
    const parts = dateStr.split('-');
    if (parts.length === 3) {
        const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
        const year = parts[0].length === 4 ? parts[0].slice(2) : parts[0];
        const monthIndex = parseInt(parts[1], 10) - 1;
        const day = parseInt(parts[2], 10);
        if (monthIndex >= 0 && monthIndex < 12) {
            return `${day} ${months[monthIndex]} ${year}`;
        }
    }
    return dateStr;
}

function formatRelativeDays(dateStr) {
    if (!dateStr) return '';
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const target = new Date(dateStr + 'T00:00:00');
    const diffDays = Math.ceil((target - today) / (1000 * 60 * 60 * 24));
    
    if (diffDays === 0) return `<span style="color: var(--accent-rose); font-weight: 700;">Today!</span>`;
    if (diffDays === 1) return `<span style="color: var(--accent-amber); font-weight: 700;">Tomorrow</span>`;
    if (diffDays > 1) return `in ${diffDays}d`;
    if (diffDays < 0) return `${Math.abs(diffDays)}d ago`;
    return '';
}

// ── Official Service Logo Resolver ──────────────────────────────────────────
function getServiceLogoUrl(serviceName, websiteUrl) {
    let domain = '';
    if (websiteUrl) {
        try {
            const parsed = new URL(websiteUrl.startsWith('http') ? websiteUrl : 'https://' + websiteUrl);
            domain = parsed.hostname.replace(/^www\./, '');
        } catch (e) {}
    }
    if (!domain && serviceName) {
        const lower = serviceName.toLowerCase();
        if (lower.includes('netflix')) domain = 'netflix.com';
        else if (lower.includes('apple') || lower.includes('icloud') || lower.includes('itunes')) domain = 'apple.com';
        else if (lower.includes('amazon') || lower.includes('prime')) domain = 'amazon.com';
        else if (lower.includes('spotify')) domain = 'spotify.com';
        else if (lower.includes('chatgpt') || lower.includes('openai')) domain = 'openai.com';
        else if (lower.includes('claude') || lower.includes('anthropic')) domain = 'claude.ai';
        else if (lower.includes('cursor')) domain = 'cursor.com';
        else if (lower.includes('github')) domain = 'github.com';
        else if (lower.includes('google') || lower.includes('youtube')) domain = 'google.com';
        else if (lower.includes('adobe')) domain = 'adobe.com';
        else if (lower.includes('figma')) domain = 'figma.com';
        else if (lower.includes('canva')) domain = 'canva.com';
        else if (lower.includes('notion')) domain = 'notion.so';
        else if (lower.includes('zoom')) domain = 'zoom.us';
        else if (lower.includes('slack')) domain = 'slack.com';
        else if (lower.includes('discord')) domain = 'discord.com';
        else if (lower.includes('microsoft') || lower.includes('office') || lower.includes('msft')) domain = 'microsoft.com';
        else if (lower.includes('disney') || lower.includes('hotstar')) domain = 'disneyplus.com';
        else if (lower.includes('nordvpn')) domain = 'nordvpn.com';
        else if (lower.includes('1password')) domain = '1password.com';
        else if (lower.includes('dropbox')) domain = 'dropbox.com';
        else if (lower.includes('hulu')) domain = 'hulu.com';
        else if (lower.includes('max') || lower.includes('hbo')) domain = 'max.com';
        else {
            const clean = lower.replace(/[^a-z0-9]/g, '');
            if (clean && clean.length > 2) domain = `${clean}.com`;
        }
    }
    return domain ? `https://www.google.com/s2/favicons?domain=${domain}&sz=128` : null;
}

// ── HTML Sanitizer Helper ───────────────────────────────────────────────────
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// ── Dark / Light Theme Manager ──────────────────────────────────────────────
function initTheme() {
    const savedTheme = localStorage.getItem('subpulse_theme') || 'dark';
    applyTheme(savedTheme);
}

function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('subpulse_theme', theme);
    const icon = document.getElementById('theme-icon');
    if (icon) {
        icon.textContent = theme === 'light' ? '☀️' : '🌙';
    }
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme') || 'dark';
    const next = current === 'dark' ? 'light' : 'dark';
    applyTheme(next);
    showToast(`Switched to ${next === 'dark' ? 'Dark Mode 🌙' : 'Light Mode ☀️'}`, 'info');
}

// ── 1-Click "Cancel Plan" Direct Guide Modal ──────────────────────────────
function openCancelModal(serviceName) {
    const modal = document.getElementById('cancel-modal');
    const titleEl = document.getElementById('cancel-modal-title');
    const contentEl = document.getElementById('cancel-modal-content');
    if (!modal || !contentEl) return;

    const info = (typeof cancelCatalog !== 'undefined') ? cancelCatalog.findService(serviceName) : null;
    const sName = info ? info.name : serviceName;

    if (titleEl) titleEl.textContent = `How to Cancel ${sName}`;

    let stepsHtml = '';
    if (info && info.steps) {
        stepsHtml = info.steps.map((step, idx) => `
            <li style="margin-bottom: 8px; line-height: 1.4;">
                <strong style="color: var(--text-primary);">${idx + 1}.</strong> ${escapeHtml(step)}
            </li>
        `).join('');
    }

    contentEl.innerHTML = `
        <div style="margin-bottom: 18px;">
            <p style="font-size: 13px; color: var(--text-secondary); margin-bottom: 14px; line-height: 1.5;">
                Follow these verified steps to cancel your recurring <strong>${escapeHtml(sName)}</strong> subscription without unexpected charges.
            </p>

            <a href="${info?.cancelUrl || '#'}" target="_blank" class="btn btn-primary" style="display: flex; align-items: center; justify-content: center; gap: 8px; width: 100%; text-decoration: none; padding: 12px; margin-bottom: 16px; font-weight: 700;">
                <span>🔗 Open ${escapeHtml(sName)} Cancellation Portal</span>
                <span>↗</span>
            </a>

            <div style="background: rgba(255, 255, 255, 0.03); border: 1px solid var(--border-subtle); border-radius: 10px; padding: 14px; margin-bottom: 14px;">
                <div style="font-size: 12px; font-weight: 700; color: var(--text-primary); margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px;">Step-by-Step Instructions:</div>
                <ol style="padding-left: 18px; margin: 0; font-size: 12.5px; color: var(--text-secondary);">
                    ${stepsHtml}
                </ol>
            </div>

            ${info?.tip ? `
                <div style="background: rgba(99, 102, 241, 0.08); border: 1px solid rgba(99, 102, 241, 0.25); border-radius: 8px; padding: 10px 12px; font-size: 11.5px; color: #a5b4fc; display: flex; align-items: center; gap: 8px;">
                    <span style="font-size: 14px;">💡</span>
                    <span><strong>Pro-Tip:</strong> ${escapeHtml(info.tip)}</span>
                </div>
            ` : ''}
        </div>
    `;

    modal.classList.add('active');
}

function closeCancelModal() {
    document.getElementById('cancel-modal')?.classList.remove('active');
}

// ── Family & Team Bill Splitter Controller ─────────────────────────────────
function populateSplitterDropdown(subscriptions) {
    const select = document.getElementById('splitter-sub-select');
    if (!select) return;

    select.innerHTML = '<option value="">-- Select a Subscription (or Enter Custom Below) --</option>';
    if (subscriptions && subscriptions.length > 0) {
        subscriptions.forEach(s => {
            select.innerHTML += `<option value="${s.id}" data-amount="${s.amount}" data-currency="${s.currency}" data-name="${escapeHtml(s.serviceName)}" data-cycle="${s.billingCycle}" data-date="${s.nextBillingDate || ''}">${escapeHtml(s.serviceName)} (${s.currency} ${Number(s.amount).toFixed(2)} / ${s.billingCycle.toLowerCase()})</option>`;
        });
    }
}

function openSplitterModal(subId = null) {
    const modal = document.getElementById('splitter-modal');
    const select = document.getElementById('splitter-sub-select');
    const amountInput = document.getElementById('splitter-total-amount');

    if (subId && select) {
        select.value = subId;
        const opt = select.selectedOptions[0];
        if (opt && amountInput) {
            amountInput.value = opt.getAttribute('data-amount') || '';
        }
    } else if (select && select.value) {
        const opt = select.selectedOptions[0];
        if (opt && amountInput && !amountInput.value) {
            amountInput.value = opt.getAttribute('data-amount') || '';
        }
    }

    recalculateSplit();
    modal?.classList.add('active');
}

function closeSplitterModal() {
    document.getElementById('splitter-modal')?.classList.remove('active');
}

function recalculateSplit() {
    const select = document.getElementById('splitter-sub-select');
    const amountInput = document.getElementById('splitter-total-amount');
    const countInput = document.getElementById('splitter-members-count');
    const perPersonEl = document.getElementById('splitter-per-person');
    const summaryEl = document.getElementById('splitter-summary-text');
    const reminderEl = document.getElementById('splitter-reminder-text');

    const total = parseFloat(amountInput?.value) || 0;
    const count = parseInt(countInput?.value) || 2;
    const opt = select?.selectedOptions[0];
    const currency = opt?.getAttribute('data-currency') || 'USD';
    const sName = (opt && opt.value) ? opt.getAttribute('data-name') : 'Subscription';
    const cycle = (opt && opt.value) ? (opt.getAttribute('data-cycle') || 'monthly').toLowerCase() : 'month';
    const dateStr = (opt && opt.value) ? (opt.getAttribute('data-date') || 'soon') : 'soon';

    const perPerson = total > 0 ? (total / count).toFixed(2) : '0.00';

    if (perPersonEl) perPersonEl.textContent = `${currency} ${perPerson} / person`;
    if (summaryEl) summaryEl.textContent = `${count} members splitting ${currency} ${total.toFixed(2)} (${cycle})`;

    if (reminderEl) {
        reminderEl.value = `Hey! 👋 Your share for ${sName} is ${currency} ${perPerson} this ${cycle} (Renewal: ${dateStr}). Please send via UPI / Bank transfer. Thanks! ⚡`;
    }
}

