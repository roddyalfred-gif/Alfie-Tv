const { getDefaultConfig } = require('metro-config');

module.exports = (async () => {
  const defaultConfig = await getDefaultConfig();
  return {
    ...defaultConfig,
    resolver: {
      ...defaultConfig.resolver,
      sourceExts: [...defaultConfig.resolver.sourceExts, 'ts', 'tsx'],
    },
  };
})();
