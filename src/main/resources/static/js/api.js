/**
 * SubPulse — REST API Client & JWT Auth Store
 */
const API_BASE = '/api/v1';

const api = {
    // ── Token Storage ───────────────────────────────────────────────────────
    getToken() {
        return localStorage.getItem('subpulse_token');
    },

    setToken(token) {
        localStorage.setItem('subpulse_token', token);
    },

    getUser() {
        const u = localStorage.getItem('subpulse_user');
        return u ? JSON.parse(u) : null;
    },

    setUser(user) {
        localStorage.setItem('subpulse_user', JSON.stringify(user));
    },

    logout() {
        localStorage.removeItem('subpulse_token');
        localStorage.removeItem('subpulse_user');
        window.location.reload();
    },

    // ── Generic Request Wrapper ─────────────────────────────────────────────
    async request(endpoint, options = {}) {
        const url = `${API_BASE}${endpoint}`;
        const headers = {
            'Content-Type': 'application/json',
            ...(options.headers || {})
        };

        const token = this.getToken();
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }

        const response = await fetch(url, {
            ...options,
            headers
        });

        if (response.status === 401) {
            this.logout();
            throw new Error('Session expired. Please log in again.');
        }

        if (response.status === 204) {
            return null;
        }

        const data = await response.json().catch(() => null);

        if (!response.ok) {
            const message = data?.message || (typeof data === 'string' ? data : `Request failed with status ${response.status}`);
            throw new Error(message);
        }

        return data;
    },

    // ── Auth Endpoints ──────────────────────────────────────────────────────
    async login(email, password) {
        const res = await this.request('/auth/login', {
            method: 'POST',
            body: JSON.stringify({ email, password })
        });
        if (res?.accessToken) {
            this.setToken(res.accessToken);
            this.setUser(res.user || { email, fullName: email.split('@')[0] });
        }
        return res;
    },

    async register(fullName, email, password) {
        const res = await this.request('/auth/register', {
            method: 'POST',
            body: JSON.stringify({ fullName, email, password })
        });
        if (res?.accessToken) {
            this.setToken(res.accessToken);
            this.setUser(res.user || { email, fullName });
        }
        return res;
    },

    async getMe() {
        const user = await this.request('/auth/me');
        if (user) {
            this.setUser(user);
        }
        return user;
    },

    // ── Subscriptions Endpoints ─────────────────────────────────────────────
    async getSubscriptions() {
        return await this.request('/subscriptions');
    },

    async getUpcoming(days = 30) {
        return await this.request(`/subscriptions/upcoming?days=${days}`);
    },

    async getAnalytics(currency = null) {
        const query = currency ? `?currency=${encodeURIComponent(currency)}` : '';
        return await this.request(`/subscriptions/analytics${query}`);
    },

    async getAiOptimization(currency = null) {
        const query = currency ? `?currency=${encodeURIComponent(currency)}` : '';
        return await this.request(`/subscriptions/ai-optimization${query}`);
    },

    async createSubscription(subscriptionData) {
        return await this.request('/subscriptions', {
            method: 'POST',
            body: JSON.stringify(subscriptionData)
        });
    },

    async updateSubscription(id, subscriptionData) {
        return await this.request(`/subscriptions/${id}`, {
            method: 'PUT',
            body: JSON.stringify(subscriptionData)
        });
    },

    async deleteSubscription(id) {
        return await this.request(`/subscriptions/${id}`, {
            method: 'DELETE'
        });
    },

    async toggleSubscription(id) {
        return await this.request(`/subscriptions/${id}/toggle`, {
            method: 'PATCH'
        });
    },

    // ── Alert Configs Endpoints ─────────────────────────────────────────────
    async getAlerts(subscriptionId) {
        return await this.request(`/subscriptions/${subscriptionId}/alerts`);
    },

    async createAlert(subscriptionId, alertData) {
        return await this.request(`/subscriptions/${subscriptionId}/alerts`, {
            method: 'POST',
            body: JSON.stringify(alertData)
        });
    },

    async deleteAlert(subscriptionId, alertId) {
        return await this.request(`/subscriptions/${subscriptionId}/alerts/${alertId}`, {
            method: 'DELETE'
        });
    },

    // ── Kafka Event Testing Endpoint ────────────────────────────────────────
    async triggerTestKafkaEvent(subscriptionId, daysRemaining = 7) {
        const url = `${API_BASE}/subscriptions/${subscriptionId}/test-alert-event?daysRemaining=${daysRemaining}`;
        const headers = { 'Authorization': `Bearer ${this.getToken()}` };
        const response = await fetch(url, { method: 'POST', headers });
        if (!response.ok) {
            throw new Error(`Kafka trigger failed with status ${response.status}`);
        }
        return await response.text();
    },

    // ── Calendar Sync Endpoints ─────────────────────────────────────────────
    async getCalendarSyncInfo() {
        return await this.request('/calendar/sync-info');
    },

    // ── Monthly Executive Digest Reports ────────────────────────────────────
    async downloadMonthlyPdf(currency = 'USD') {
        const url = `${API_BASE}/reports/monthly-digest/pdf?currency=${currency}`;
        const token = this.getToken();
        const response = await fetch(url, {
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) {
            throw new Error('Failed to generate PDF report');
        }

        const blob = await response.blob();
        const downloadUrl = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = downloadUrl;
        a.download = `SubPulse_Monthly_Digest_${new Date().toISOString().slice(0,7)}.pdf`;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(downloadUrl);
    },

    async sendMonthlyEmailDigest(currency = 'USD') {
        const url = `${API_BASE}/reports/monthly-digest/send-email?currency=${currency}`;
        const token = this.getToken();
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` }
        });

        if (!response.ok) {
            const err = await response.json().catch(() => null);
            throw new Error(err?.message || 'Failed to dispatch monthly email digest');
        }

        return await response.text();
    },

    // ── Bank Statement & CSV Auto-Import ────────────────────────────────────
    async previewCsvImport(file) {
        const formData = new FormData();
        formData.append('file', file);

        const url = `${API_BASE}/subscriptions/import/preview`;
        const token = this.getToken();
        const response = await fetch(url, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${token}` },
            body: formData
        });

        if (!response.ok) {
            const err = await response.json().catch(() => null);
            throw new Error(err?.message || 'Failed to parse CSV statement');
        }

        return await response.json();
    },

    async confirmCsvImport(subscriptions) {
        return await this.request('/subscriptions/import/confirm', {
            method: 'POST',
            body: JSON.stringify({ subscriptions })
        });
    },

    downloadCsvTemplate() {
        const url = `${API_BASE}/subscriptions/import/template`;
        window.open(url, '_blank');
    }
};
