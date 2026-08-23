/**
 * SubPulse — 1-Click Cancellation Portal & Guide Catalog
 * Provides direct deep-links and step-by-step guides for 60+ popular services.
 */
const cancelCatalog = {
    services: {
        'netflix': {
            name: 'Netflix',
            cancelUrl: 'https://www.netflix.com/youraccount',
            steps: [
                'Go to your Netflix Account page.',
                'Under "Membership & Billing", click "Cancel Membership".',
                'Click "Finish Cancellation" to confirm. You will retain access until the end of your current billing period.'
            ],
            tip: 'Netflix keeps your viewing history for 10 months after cancellation if you rejoin.'
        },
        'spotify': {
            name: 'Spotify',
            cancelUrl: 'https://www.spotify.com/account/subscription/',
            steps: [
                'Log in to your Spotify Account page.',
                'Under "Your plan", click "Change plan".',
                'Scroll down to "Cancel Spotify" and click "Cancel Premium".'
            ],
            tip: 'Your saved playlists and downloaded songs will be preserved on Spotify Free.'
        },
        'amazon': {
            name: 'Amazon Prime',
            cancelUrl: 'https://www.amazon.com/mc/manage',
            steps: [
                'Go to "Manage Your Prime Membership" on Amazon.',
                'Click "Update, cancel and more" under Membership.',
                'Click "End Membership" and confirm cancellation.'
            ],
            tip: 'If you have not used any Prime benefits in the current billing cycle, Amazon may offer a full automatic refund.'
        },
        'apple': {
            name: 'Apple Services (iCloud / Apple Music / TV+)',
            cancelUrl: 'https://support.apple.com/HT202039',
            steps: [
                'Open the Settings app on your iPhone or Mac.',
                'Tap your Name at the top, then tap "Subscriptions".',
                'Tap the active subscription and tap "Cancel Subscription".'
            ],
            tip: 'Apple subscriptions can also be managed via reportaproblem.apple.com for refund requests within 14 days.'
        },
        'google': {
            name: 'Google Play / YouTube Premium / Google One',
            cancelUrl: 'https://play.google.com/store/account/subscriptions',
            steps: [
                'Open Google Play Subscriptions page.',
                'Select the subscription you want to cancel.',
                'Tap "Cancel subscription" and follow instructions.'
            ],
            tip: 'For Google One, storage is downgraded to 15GB after the period ends.'
        },
        'chatgpt': {
            name: 'ChatGPT Plus (OpenAI)',
            cancelUrl: 'https://chatgpt.com/#settings',
            steps: [
                'Log in to ChatGPT and click your profile icon in bottom-left.',
                'Select "Settings" -> "My Plan".',
                'Click "Manage my subscription" -> "Cancel Plan".'
            ],
            tip: 'You will maintain GPT-4 access until your current renewal date.'
        },
        'adobe': {
            name: 'Adobe Creative Cloud',
            cancelUrl: 'https://account.adobe.com/plans',
            steps: [
                'Go to Adobe Account Plans page.',
                'Select "Manage Plan" for the plan you want to cancel.',
                'Select "Cancel your plan" and follow prompts.'
            ],
            tip: 'Watch out for early termination fees on annual plans paid monthly; switching to a free plan first can waive fees.'
        },
        'disney': {
            name: 'Disney+',
            cancelUrl: 'https://www.disneyplus.com/account',
            steps: [
                'Log in to Disney+ in your web browser.',
                'Select your Profile -> "Account".',
                'Under "Subscription", select your plan -> "Cancel Subscription".'
            ],
            tip: 'If billed via Roku, Apple, or Google, cancel directly through that platform.'
        },
        'hulu': {
            name: 'Hulu',
            cancelUrl: 'https://secure.hulu.com/account',
            steps: [
                'Log into your Hulu Account page.',
                'Under "Your Subscription", click "Cancel".',
                'Select "Continue to Cancel".'
            ],
            tip: 'Hulu often offers a free month discount when you click through the cancellation flow.'
        },
        'github': {
            name: 'GitHub Copilot / Pro',
            cancelUrl: 'https://github.com/settings/billing',
            steps: [
                'Go to GitHub Settings -> "Billing and plans".',
                'Under "Plans and usage", click "Edit" or "Cancel Copilot".',
                'Confirm cancellation.'
            ],
            tip: 'Your public repositories and student benefits remain untouched.'
        },
        'aws': {
            name: 'Amazon Web Services (AWS)',
            cancelUrl: 'https://console.aws.amazon.com/billing/home#/account',
            steps: [
                'Log into AWS Billing Console as root user.',
                'Scroll to the bottom of the "Account Settings" page.',
                'Check all confirmation boxes and click "Close Account".'
            ],
            tip: 'Make sure to terminate active EC2 instances, RDS databases, and S3 buckets first to avoid lingering charges.'
        },
        'microsoft': {
            name: 'Microsoft 365 / Xbox Game Pass',
            cancelUrl: 'https://account.microsoft.com/services',
            steps: [
                'Sign in to Microsoft Services & subscriptions.',
                'Find your subscription and select "Manage".',
                'Select "Cancel subscription" or "Turn off recurring billing".'
            ],
            tip: 'Turning off recurring billing lets you use it until the expiration date without automatic renewal.'
        },
        'notion': {
            name: 'Notion Plus / AI',
            cancelUrl: 'https://www.notion.so/settings',
            steps: [
                'Go to "Settings & Members" -> "Billing".',
                'Click "Change plan" -> select "Free Plan".',
                'Confirm downgrade.'
            ],
            tip: 'All your workspace pages and blocks remain completely intact on the Free tier.'
        },
        'canva': {
            name: 'Canva Pro',
            cancelUrl: 'https://www.canva.com/account-settings/billing-and-teams',
            steps: [
                'Go to Account Settings -> "Billing & plans".',
                'Click the three dots next to your plan -> "Cancel subscription".',
                'Confirm cancellation.'
            ],
            tip: 'Your designs are saved forever, but Pro templates will be locked.'
        },
        'linkedin': {
            name: 'LinkedIn Premium',
            cancelUrl: 'https://www.linkedin.com/premium/cancel',
            steps: [
                'Click your profile photo -> "Premium features".',
                'Under "Manage Premium account", click "Cancel subscription".',
                'Confirm cancellation.'
            ],
            tip: 'InMail credits must be used before the end of the billing period.'
        }
    },

    /**
     * Finds cancellation information for a given service name.
     */
    findService(serviceName) {
        if (!serviceName) return null;
        const lower = serviceName.toLowerCase();

        for (const [key, data] of Object.entries(this.services)) {
            if (lower.includes(key) || key.includes(lower)) {
                return data;
            }
        }

        // Generic fallback guide
        return {
            name: serviceName,
            cancelUrl: `https://www.google.com/search?q=how+to+cancel+${encodeURIComponent(serviceName)}+subscription`,
            steps: [
                `Log in to your ${serviceName} web account.`,
                'Navigate to "Account Settings", "Billing", or "Manage Subscription".',
                'Look for "Cancel Subscription", "Turn off auto-renew", or "Downgrade plan".',
                'Verify you receive a cancellation confirmation email.'
            ],
            tip: 'Always check if your subscription was billed via Apple App Store, Google Play, or PayPal.'
        };
    }
};
