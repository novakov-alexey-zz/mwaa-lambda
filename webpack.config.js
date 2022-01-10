const sjsConfig = require('./scalajs.webpack.config');
sjsConfig.output.libraryTarget = 'commonjs2';
sjsConfig.target = 'node';

module.exports = sjsConfig
module.exports.mode = 'development'  // 'none' | 'development' | 'production'