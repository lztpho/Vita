// SPDX-License-Identifier: Apache-2.0
import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'io.github.lztpho.vita',
  appName: 'Vita',
  webDir: 'dist',
  backgroundColor: '#F6F7FB',
  android: {
    backgroundColor: '#F6F7FB',
    allowMixedContent: false,
    captureInput: true,
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 800,
      backgroundColor: '#252A5A',
      showSpinner: false,
    },
  },
};

export default config;
