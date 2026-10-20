import createNextIntlPlugin from "next-intl/plugin";
import nextPwa from "next-pwa";

const withNextIntl = createNextIntlPlugin("./i18n/request.ts");
const withPWA = nextPwa({
  dest: "public",
  register: true,
  skipWaiting: true,
  // PWA can be noisy/brittle in containerized dev; enable explicitly when needed.
  disable: process.env.PWA_ENABLED !== "true",
});

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  async redirects() {
    return [
      { source: "/dashboard/budget", destination: "/budget", permanent: false },
      { source: "/dashboard/inventaire", destination: "/inventaire", permanent: false },
      { source: "/dashboard/rapports", destination: "/rapports", permanent: false },
    ];
  },
};

export default withPWA(withNextIntl(nextConfig));
