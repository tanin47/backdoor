"use strict";

const webpack = require('webpack');
const sveltePreprocess = require("svelte-preprocess");
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const pathModule = require("path");
const glob = require('glob');
const fs = require('fs');

const aptabaseIdPath = './secret/APTABASE_ID';
let aptabaseId = process.env.APTABASE_ID;
if (fs.existsSync(aptabaseIdPath)) {
  aptabaseId = fs.readFileSync(aptabaseIdPath, 'utf8').trim();
}


function buildEntry(mode) {
  let entry = {}
  for (const relativePath of glob.globSync('./frontend/svelte/**/*.svelte')) {
    if (pathModule.basename(relativePath).startsWith('_')) {
      continue
    }

    const key = relativePath.substring('frontend/'.length, relativePath.length - '.svelte'.length)

    if (mode === 'development') {
      entry[key] = [
        "webpack-hot-middleware/client?path=http://localhost:8090/__webpack_hmr&timeout=5000&reload=true",
        `./${relativePath}`
      ]
    } else {
      entry[key] = `./${relativePath}`
    }
  }

  if (mode === 'development') {
    entry['stylesheets/tailwindbase'] = [
      `webpack-hot-middleware/client?path=http://localhost:8090/__webpack_hmr&timeout=5000&reload=true`,
      './frontend/stylesheets/tailwindbase.css',
    ]
  }

  return entry
}

const replacePathVariables = (path, data) => {
  const REGEXP_CAMEL_CASE_NAME = /\[camel-case-name\]/gi;
  if (typeof path === "function") {
    path = path(data);
  }

  if (data && data.chunk && data.chunk.name) {
    let tokens = data.chunk.name.split(pathModule.sep);
    return path.replace(
      REGEXP_CAMEL_CASE_NAME,
      tokens[tokens.length - 1]
        .replace(/(\-\w)/g, (matches) => {
          return matches[1].toUpperCase();
        })
        .replace(/(^\w)/, (matches) => {
          return matches[0].toUpperCase();
        })
    );
  } else {
    return path;
  }
};

class CamelCaseNamePlugin {
  apply(compiler) {
    compiler.hooks.compilation.tap("sbt-js-compilation", (compilation) => {
      compilation.hooks.assetPath.tap('sbt-js-asset-path', replacePathVariables);
    });
  }
}

const config = {
  mode: 'development',
  cache: true,
  stats: 'minimal',
  entry: [],
  resolve: {
    extensions: ['.mjs', '.js', '.svelte', '.ts'],
    mainFields: ['svelte', 'browser', 'module', 'main'],
    conditionNames: ['svelte', 'browser', 'import']
  },
  module: {
    rules: [
      {
        test: /\.svelte(\.ts)?$/,
        use: {
          loader: 'svelte-loader',
          options: {
            emitCss: false,
            preprocess: sveltePreprocess({}),
            compilerOptions: {
              dev: false,
              compatibility: {
                componentApi: 4
              }
            },
            hotReload: false,
            onwarn: (warning, handler) => {
              const {code} = warning
              if (code.startsWith('a11y')) return
              if (code === 'css-unused-selector') return
              if (code === 'export_let_unused') return

              // Throw exception on warning. This is because unrecognized variable is also a warning.
              throw warning
            },
          }
        }
      },
      {
        test: /\.css$/,
        // TODO: target only codemirror
        // exclude: /node_modules/,
        use: [
          MiniCssExtractPlugin.loader,
          'css-loader',
          'postcss-loader',
        ],
      },
      {
        test: /\.ts$/,
        use: 'ts-loader',
        exclude: /node_modules/,
      },
    ]
  },
  plugins: [
    new MiniCssExtractPlugin(),
    new CamelCaseNamePlugin(),
    new webpack.DefinePlugin({
      'APTABASE_ID_WEBPACK_REPLACEMENT': JSON.stringify(aptabaseId),
    }),
  ],
  output: {
    publicPath: '/assets/',
    library: '[camel-case-name]',
    filename: '[name].js',
  },
  performance: {
    hints: 'error',
    maxAssetSize: 10000000,
    maxEntrypointSize: 10000000,
    assetFilter: function (assetFilename) {
      return assetFilename.endsWith('.js');
    }
  },
  devtool: 'eval-cheap-source-map',
};

module.exports = (env, argv) => {
  if (argv.mode === 'production') {
    console.log('Webpack for production');
    config.devtool = false;
    config.performance.maxAssetSize = 500000;
    config.performance.maxEntrypointSize = 500000;
    config.optimization = (config.optimization || {});
    config.entry = buildEntry('production');
  } else if (argv.mode === 'development') {
    console.log('Webpack for development')
    config.entry = buildEntry('development');

    if (process.env.ENABLE_HMR) {
      console.log('Enable HMR')
      for (const rule of config.module.rules) {
        if (rule.use.loader === 'svelte-loader') {
          rule.use.options.emitCss = false
          rule.use.options.compilerOptions.dev = true
          rule.use.options.hotReload = true
        }
      }
      config.plugins.push(new webpack.HotModuleReplacementPlugin())

      // The below config will make HMR work when a new file is added or removed.
      config.watchOptions = {
        aggregateTimeout: 300,
        poll: 1000
      }
    }
  } else if (argv.mode === 'none') {

  } else {
    throw new Error('argv.mode must be either development, none, or production.')
  }

  return config;
};
