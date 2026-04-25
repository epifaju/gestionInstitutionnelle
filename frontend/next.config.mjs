import createNextIntlPlugin from "next-intl/plugin";
import nextPwa from "next-pwa";

const withNextIntl = createNextIntlPlugin("./i18n/request.ts");
const withPWA = nextPwa({
  dest: "public",
  register: true,
  skipWaiting: true,
  disable: process.env.NODE_ENV === "development",
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
