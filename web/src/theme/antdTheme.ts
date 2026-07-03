import type { ThemeConfig } from 'antd';
import { policeTokens } from './tokens';

export const antdTheme: ThemeConfig = {
  token: {
    colorPrimary: policeTokens.PolicePrimary,
    colorLink: policeTokens.PoliceActionBlue,
    colorSuccess: policeTokens.StatusNormal,
    colorWarning: policeTokens.StatusWarning,
    colorError: policeTokens.StatusError,
    colorText: policeTokens.TextPrimary,
    colorTextSecondary: policeTokens.TextSecondary,
    colorBorder: policeTokens.BorderColor,
    colorBgLayout: policeTokens.PageBackground,
    colorBgContainer: policeTokens.SurfaceWhite,
    borderRadius: 6,
    controlHeight: 40,
    controlHeightLG: 44,
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
  },
  components: {
    Button: {
      borderRadius: 6,
      controlHeight: 44,
      controlHeightLG: 48,
      fontWeight: 600,
    },
    Card: {
      borderRadiusLG: 8,
      boxShadowTertiary: '0 1px 2px rgba(12, 35, 72, 0.04)',
    },
    Table: {
      headerBg: policeTokens.SurfaceMuted,
      headerColor: policeTokens.TextPrimary,
      rowHoverBg: policeTokens.PolicePale,
    },
    Layout: {
      headerBg: policeTokens.PoliceNavy,
      siderBg: policeTokens.SurfaceWhite,
    },
    Menu: {
      itemSelectedBg: policeTokens.PoliceLight,
      itemSelectedColor: policeTokens.PolicePrimary,
    },
  },
};
