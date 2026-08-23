/**
 * SubPulse — Interactive Visualizations & Spend Forecasting Engine
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
        'HEALTH_FITNESS': '#10b981',
        'HEALTH': '#10b981',
        'OTHER': '#64748b'
    },

    forecastChartInstance: null,
    donutChartInstance: null,

    getColor(category) {
        return this.categoryColors[category] || '#6366f1';
    },

    parseLocalDate(dateInput) {
        if (!dateInput) return null;
        if (typeof dateInput === 'string') {
            const parts = dateInput.split('T')[0].split('-');
            if (parts.length === 3) {
                const y = parseInt(parts[0], 10);
                const m = parseInt(parts[1], 10) - 1; // 0-indexed month
                const d = parseInt(parts[2], 10);
                if (!isNaN(y) && !isNaN(m) && !isNaN(d)) {
                    return new Date(y, m, d);
                }
            }
        }
        const parsed = new Date(dateInput);
        return isNaN(parsed.getTime()) ? null : parsed;
    },

    /**
     * 12-Month Spend Forecast Chart
     * Projects monthly cost forward across the upcoming 12 months strictly within active date boundaries.
     */
    renderForecastChart(canvasId, subscriptions, currency = 'USD') {
        const canvas = document.getElementById(canvasId);
        if (!canvas || typeof Chart === 'undefined') return;

        const months = [];
        const monthlyTotals = new Array(12).fill(0);
        const now = new Date();

        for (let i = 0; i < 12; i++) {
            const d = new Date(now.getFullYear(), now.getMonth() + i, 1);
            const m = d.toLocaleString('default', { month: 'short' });
            const y = String(d.getFullYear()).slice(-2);
            months.push(`${m} '${y}`);
        }

        // Calculate cost per month strictly within active date boundaries
        if (subscriptions && subscriptions.length > 0) {
            subscriptions.forEach(sub => {
                if (!sub.isActive) return;
                const amt = Number(sub.amount) || 0;
                const cycle = (sub.billingCycle || 'MONTHLY').toUpperCase();
                const startDate = this.parseLocalDate(sub.startDate);
                const renewDate = this.parseLocalDate(sub.nextBillingDate);
                const trialEnd = this.parseLocalDate(sub.trialEndDate);
                const isAutoRenew = sub.autoRenew !== false;

                // Month boundary dates (1st of the month)
                const startMonthDate = startDate ? new Date(startDate.getFullYear(), startDate.getMonth(), 1) : null;
                const endMonthDate = (!isAutoRenew && renewDate)
                    ? new Date(renewDate.getFullYear(), renewDate.getMonth(), 1)
                    : (trialEnd ? new Date(trialEnd.getFullYear(), trialEnd.getMonth(), 1) : null);

                for (let i = 0; i < 12; i++) {
                    const targetMonthDate = new Date(now.getFullYear(), now.getMonth() + i, 1);

                    // 1. If target month is before subscription's start month -> don't charge
                    if (startMonthDate && targetMonthDate < startMonthDate) {
                        continue;
                    }

                    // 2. If subscription does not auto-renew and target month is after end/renewal month -> don't charge
                    if (endMonthDate && targetMonthDate > endMonthDate) {
                        continue;
                    }

                    // 3. Billing cycle rules
                    if (cycle === 'MONTHLY') {
                        monthlyTotals[i] += amt;
                    } else if (cycle === 'ANNUAL' || cycle === 'YEARLY') {
                        if (renewDate && renewDate.getMonth() === targetMonthDate.getMonth()) {
                            monthlyTotals[i] += amt;
                        }
                    } else if (cycle === 'QUARTERLY') {
                        if (renewDate && (targetMonthDate.getMonth() - renewDate.getMonth() + 12) % 3 === 0) {
                            monthlyTotals[i] += amt;
                        }
                    } else if (cycle === 'WEEKLY') {
                        monthlyTotals[i] += amt * 4.33;
                    }
                }
            });
        }

        if (this.forecastChartInstance) {
            this.forecastChartInstance.destroy();
        }

        const isLight = document.documentElement.getAttribute('data-theme') === 'light';
        const textColor = isLight ? '#1e1b4b' : '#f8fafc';
        const gridColor = isLight ? 'rgba(109, 40, 217, 0.08)' : 'rgba(255, 255, 255, 0.06)';

        const ctx = canvas.getContext('2d');
        const gradient = ctx.createLinearGradient(0, 0, 0, 200);
        gradient.addColorStop(0, 'rgba(99, 102, 241, 0.85)');
        gradient.addColorStop(1, 'rgba(139, 92, 246, 0.25)');

        this.forecastChartInstance = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: months,
                datasets: [{
                    label: `Projected Spend (${currency})`,
                    data: monthlyTotals.map(v => Number(v.toFixed(2))),
                    backgroundColor: gradient,
                    borderColor: '#6366f1',
                    borderWidth: 1.5,
                    borderRadius: 6,
                    borderSkipped: false,
                    hoverBackgroundColor: '#8b5cf6'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: isLight ? '#ffffff' : '#172033',
                        titleColor: textColor,
                        bodyColor: textColor,
                        borderColor: 'rgba(99, 102, 241, 0.3)',
                        borderWidth: 1,
                        padding: 10,
                        callbacks: {
                            label: (context) => ` ${currency} ${context.parsed.y.toFixed(2)}`
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { display: false },
                        ticks: { color: isLight ? '#6b7280' : '#94a3b8', font: { family: 'Plus Jakarta Sans', size: 11, weight: '600' } }
                    },
                    y: {
                        grid: { color: gridColor },
                        ticks: {
                            color: isLight ? '#6b7280' : '#94a3b8',
                            font: { family: 'Plus Jakarta Sans', size: 10 },
                            callback: (v) => `${currency} ${v}`
                        }
                    }
                }
            }
        });
    },

    /**
     * Category Distribution Donut Chart
     */
    renderCategoryDonutChart(canvasId, spendByCategory, currency = 'USD') {
        const canvas = document.getElementById(canvasId);
        if (!canvas || typeof Chart === 'undefined') return;

        if (!spendByCategory || Object.keys(spendByCategory).length === 0) {
            canvas.style.display = 'none';
            return;
        }
        canvas.style.display = 'block';

        const labels = Object.keys(spendByCategory).map(c => c.replace('_', ' '));
        const data = Object.values(spendByCategory).map(v => Number(v));
        const backgroundColors = Object.keys(spendByCategory).map(c => this.getColor(c));

        if (this.donutChartInstance) {
            this.donutChartInstance.destroy();
        }

        const isLight = document.documentElement.getAttribute('data-theme') === 'light';
        const textColor = isLight ? '#1e1b4b' : '#f8fafc';

        const ctx = canvas.getContext('2d');
        this.donutChartInstance = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: labels,
                datasets: [{
                    data: data,
                    backgroundColor: backgroundColors,
                    borderWidth: 2,
                    borderColor: isLight ? '#ffffff' : '#0f172a',
                    hoverOffset: 6
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '72%',
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            color: isLight ? '#4b5563' : '#94a3b8',
                            font: { family: 'Plus Jakarta Sans', size: 10.5, weight: '600' },
                            boxWidth: 10,
                            padding: 10
                        }
                    },
                    tooltip: {
                        backgroundColor: isLight ? '#ffffff' : '#172033',
                        titleColor: textColor,
                        bodyColor: textColor,
                        borderColor: 'rgba(99, 102, 241, 0.3)',
                        borderWidth: 1,
                        padding: 10,
                        callbacks: {
                            label: (context) => ` ${context.label}: ${currency} ${context.parsed.toFixed(2)}`
                        }
                    }
                }
            }
        });
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
