/**
 * SubPulse — Category Spend Visualizations
 */
const charts = {
    categoryColors: {
        'DEVELOPER_TOOLS': '#6366f1',
        'ENTERTAINMENT': '#f43f5e',
        'PRODUCTIVITY': '#06b6d4',
        'CLOUD_STORAGE': '#3b82f6',
        'AI_TOOLS': '#a855f7',
        'DESIGN': '#ec4899',
        'MARKETING': '#f59e0b',
        'FINANCE': '#10b981',
        'SECURITY': '#14b8a6',
        'COMMUNICATION': '#8b5cf6',
        'EDUCATION': '#eab308',
        'OTHER': '#64748b'
    },

    getColor(category) {
        return this.categoryColors[category] || '#6366f1';
    },

    renderCategoryBars(containerId, spendByCategory, currency = 'USD') {
        const container = document.getElementById(containerId);
        if (!container) return;

        if (!spendByCategory || Object.keys(spendByCategory).length === 0) {
            container.innerHTML = `<p style="color: var(--text-muted); font-size: 13px; text-align: center; padding: 20px 0;">No spend data recorded yet.</p>`;
            return;
        }

        const entries = Object.entries(spendByCategory);
        const maxSpend = Math.max(...entries.map(([, amt]) => Number(amt)));
        const totalSpend = entries.reduce((sum, [, amt]) => sum + Number(amt), 0);

        let html = '<div class="category-bars">';
        entries.sort((a, b) => Number(b[1]) - Number(a[1])).forEach(([cat, amount]) => {
            const num = Number(amount);
            const percent = totalSpend > 0 ? Math.round((num / totalSpend) * 100) : 0;
            const barWidth = maxSpend > 0 ? Math.round((num / maxSpend) * 100) : 0;
            const color = this.getColor(cat);

            html += `
                <div class="category-bar-item">
                    <div class="category-bar-label">
                        <span><strong>${cat.replace('_', ' ')}</strong> (${percent}%)</span>
                        <span style="color: var(--text-primary); font-weight: 600;">${currency} ${num.toFixed(2)}</span>
                    </div>
                    <div class="progress-track">
                        <div class="progress-fill" style="width: ${barWidth}%; background: ${color};"></div>
                    </div>
                </div>
            `;
        });
        html += '</div>';

        container.innerHTML = html;
    }
};
