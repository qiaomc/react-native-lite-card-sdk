const path = require('path');
const { getDefaultConfig, mergeConfig } = require('@react-native/metro-config');

const root = path.resolve(__dirname, '..');
const pak = require('../package.json');

/**
 * Metro configuration for yarn workspace (example + library root).
 * Avoids react-native-monorepo-config which is ESM-only and breaks require().
 *
 * @type {import('metro-config').MetroConfig}
 */
const config = {
  watchFolders: [root],
  resolver: {
    extraNodeModules: {
      [pak.name]: root,
    },
    resolveRequest: (context, moduleName, platform) => {
      if (moduleName === pak.name || moduleName.startsWith(`${pak.name}/`)) {
        return context.resolveRequest(
          {
            ...context,
            mainFields: ['source', ...context.mainFields],
            unstable_conditionNames: [
              'source',
              ...(context.unstable_conditionNames ?? []),
            ],
          },
          moduleName,
          platform
        );
      }

      return context.resolveRequest(context, moduleName, platform);
    },
  },
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
