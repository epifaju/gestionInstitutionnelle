import createNextIntlPlugin from "next-intl/plugin";

const withNextIntl = createNextIntlPlugin("./i18n/request.ts");

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

export default withNextIntl(nextConfig);
