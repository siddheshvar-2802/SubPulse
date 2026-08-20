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
        toast.style.transform = 'translateY(10px)';
        setTimeout(() => toast.remove(), 300);
    }, 4000);
}

// ── Init & View Routing ─────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', () => {
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
    document.getElementById('btn-logout')?.addEventListener('click', () => {
        if (confirm('Are you sure you want to log out of SubPulse?')) {
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

    // Alert Config Modal close
    document.getElementById('alert-modal-close')?.addEventListener('click', () => {
        closeAlertModal();
    });

    document.getElementById('alert-form')?.addEventListener('submit', handleAlertSubmit);
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
        charts.renderCategoryBars('category-bars-container', analytics?.spendByCategory, analytics?.currency);

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
            <div style="background: rgba(15, 23, 42, 0.85); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 12px; padding: 16px; display: flex; flex-direction: column; justify-content: space-between; transition: var(--transition);">
                <div>
                    <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
                        <span class="badge ${typeBadgeClass}" style="font-size: 11px;">${typeLabel}</span>
                        <span style="font-weight: 800; font-size: 12.5px; color: #34d399;">${savingsText}</span>
                    </div>
                    <div style="font-weight: 700; font-size: 13.5px; color: #fff; margin-bottom: 4px;">${rec.title}</div>
                    <div style="font-size: 12px; color: var(--text-muted); line-height: 1.4;">${rec.description}</div>
                </div>
                ${rec.actionUrl ? `
                    <div style="margin-top: 14px;">
                        <a href="${rec.actionUrl}" target="_blank" class="btn btn-secondary btn-sm" style="width: 100%; text-align: center; text-decoration: none; display: block; font-size: 12px;">
                            ${rec.actionLabel || 'Manage Plan'} ↗
                        </a>
                    </div>
                ` : ''}
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

        html += `
            <div class="upcoming-item">
                <div>
                    <div style="font-weight: 600; font-size: 13.5px;">${sub.serviceName}</div>
                    <div style="font-size: 11.5px; color: var(--text-muted);">${sub.currency} ${Number(sub.amount).toFixed(2)} / ${sub.billingCycle.toLowerCase()}</div>
                </div>
                <span class="badge ${badgeClass}">${label}</span>
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

    let html = '';
    subscriptions.forEach(sub => {
        const initial = sub.serviceName ? sub.serviceName[0].toUpperCase() : 'S';
        const color = charts.getColor(sub.category);

        html += `
            <tr>
                <td>
                    <div class="service-cell">
                        <div class="service-icon" style="background: ${color}22; color: ${color}; border-color: ${color}44;">${initial}</div>
                        <div>
                            <div class="service-name">${sub.serviceName}</div>
                            <div class="service-desc">${sub.description || 'No description'}</div>
                        </div>
                    </div>
                </td>
                <td>
                    <span class="badge badge-category">${sub.category.replace('_', ' ')}</span>
                </td>
                <td>
                    <div style="font-weight: 700; color: #fff;">${sub.currency} ${Number(sub.amount).toFixed(2)}</div>
                    <span class="badge badge-cycle">${sub.billingCycle}</span>
                </td>
                <td>
                    <div style="font-weight: 500;">${sub.nextBillingDate}</div>
                </td>
                <td>
                    <label class="toggle-switch">
                        <input type="checkbox" ${sub.isActive ? 'checked' : ''} onchange="toggleSubscriptionStatus(${sub.id})">
                        <span class="slider"></span>
                    </label>
                </td>
                <td>
                    <div style="display: flex; gap: 8px; align-items: center;">
                        <button class="btn btn-secondary btn-sm" onclick="openAlertModal(${sub.id}, '${sub.serviceName}')" title="Configure renewal alerts">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: var(--accent-amber);">
                                <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                                <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                            </svg>
                            <span>Alerts</span>
                        </button>
                        <button class="btn btn-secondary btn-sm" onclick="triggerKafkaEvent(${sub.id})" title="Trigger instant alert ping">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="color: #fbbf24;">
                                <polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon>
                            </svg>
                            <span>Ping</span>
                        </button>
                        <button class="btn btn-secondary btn-sm" onclick="editSubscription(${sub.id})" title="Edit subscription">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path>
                                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path>
                            </svg>
                            <span>Edit</span>
                        </button>
                        <button class="btn btn-outline-danger btn-sm" onclick="deleteSubscription(${sub.id})" title="Delete subscription">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                                <polyline points="3 6 5 6 21 6"></polyline>
                                <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                                <line x1="10" y1="11" x2="10" y2="17"></line>
                                <line x1="14" y1="11" x2="14" y2="17"></line>
                            </svg>
                            <span>Delete</span>
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

async function deleteSubscription(id) {
    if (!confirm('Are you sure you want to delete this subscription?')) return;
    try {
        await api.deleteSubscription(id);
        showToast('Subscription removed.', 'success');
        loadDashboardData();
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ── Alert Config Rules Modal ────────────────────────────────────────────────
async function openAlertModal(subscriptionId, serviceName) {
    currentAlertSubscriptionId = subscriptionId;
    document.getElementById('alert-modal-title').textContent = `Alert Rules: ${serviceName}`;
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
            html += `
                <div style="display: flex; align-items: center; justify-content: space-between; padding: 10px 14px; background: rgba(255,255,255,0.03); border: 1px solid var(--border-subtle); border-radius: 8px; margin-bottom: 8px;">
                    <div>
                        <div style="font-weight: 600; font-size: 13px;">🔔 Notify ${r.daysBefore} day(s) before</div>
                        <div style="font-size: 11px; color: var(--text-muted);">via <strong>${r.channel}</strong> (${r.destination || 'default email'})</div>
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
    try {
        await api.deleteAlert(currentAlertSubscriptionId, alertId);
        showToast('Alert rule deleted.', 'info');
        loadAlertRules(currentAlertSubscriptionId);
    } catch (err) {
        showToast(err.message, 'error');
    }
}

// ── Manual Alert Ping / Kafka Event Trigger ───────────────────────────────
async function triggerKafkaEvent(subscriptionId) {
    try {
        const msg = await api.triggerTestKafkaEvent(subscriptionId, 7);
        showToast('⚡ Alert Ping dispatched successfully!', 'success');
    } catch (err) {
        showToast('Ping error: ' + err.message, 'error');
    }
}
