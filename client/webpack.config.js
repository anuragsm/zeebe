var webpack = require('webpack');
var path = require('path');
var HtmlWebpackPlugin = require('html-webpack-plugin');
var ServiceWorkerWebpackPlugin = require('serviceworker-webpack-plugin');
var version = require('./scripts/version');

module.exports = {
  cache: true,
  devtool: 'source-map',
  target: 'web',
  entry: {
    app: path.resolve(__dirname, 'app', 'app.js'),
    license: path.resolve(__dirname, 'app', 'licenseApp.js'),
    error: path.resolve(__dirname, 'app', 'error.js')
  },
  output: {
    publicPath: '/',
    filename: '[name].[hash].js',
    path: path.join(__dirname, 'dist')
  },
  resolve: {
    root: path.resolve(__dirname, 'app'),
    modules: [
      'node_modules'
    ]
  },
  module: {
    preLoaders: [
      {
        test: /\.js$/,
        loader: 'eslint-loader',
        include: [
          path.resolve(__dirname, 'app'),
          path.resolve(__dirname, 'test')
        ]
      }
    ],
    loaders: [
      {
        test: /\.html$/,
        loader: 'html-loader',
        include: [
          path.resolve(__dirname, 'app'),
        ]
      },
      {
        test: /\.json$/,
        loader: 'json-loader'
      },
      {
        test: /\.less$/,
        loaders: [
          'style-loader',
          'css-loader?sourceMap',
          'less-loader?sourceMap'
        ],
        include: [
          path.resolve(__dirname, 'app'),
        ]
      },
      {
        test: /\.(eot|svg|ttf|woff|woff2)$/,
        loader: 'file-loader?name=fonts/[name].[ext]'
      },
      {
        test: /\.ico$/,
        loader: 'file-loader?name=[name].[ext]'
      },
      {
        test: /\.(jpg|png)$/,
        loader: 'url-loader',
        include: [
          path.resolve(__dirname, 'app'),
        ]
      },
      {
        test: /\.gif$/,
        loader: 'url-loader',
        include: [
          path.resolve(__dirname, 'app'),
        ]
      },
      {
        test: /\.js$/,
        include: [
          path.resolve(__dirname, 'app'),
        ],
        loader: 'babel-loader',
        query: {
          cacheDirectory: true,
          presets: ['latest'],
          plugins: [
            'transform-object-rest-spread',
            ['transform-react-jsx', {
              'pragma': 'jsx'
            }]
          ]
        }
      }
    ]
  },
  plugins: [
    new webpack.DefinePlugin({
      'process.env.version': JSON.stringify(version)
    }),
    new HtmlWebpackPlugin({
      title: 'Camunda Optimize',
      template: 'app/index.html',
      favicon: 'app/favicon.ico',
      chunks: ['app']
    }),
    new HtmlWebpackPlugin({
      filename: 'license.html',
      title: 'Camunda Optimize',
      template: 'app/license.html',
      favicon: 'app/favicon.ico',
      chunks: ['license']
    }),
    new HtmlWebpackPlugin({
      filename: 'error.html',
      title: 'Camunda Optimize',
      template: 'app/error.html',
      favicon: 'app/favicon.ico',
      chunks: ['error']
    }),
    new ServiceWorkerWebpackPlugin({
      entry: path.join(__dirname, 'app/sw.js')
    })
  ],
  devServer: {
    contentBase: './dist',
    port: 9000,
    inline: true,
    open: true,
    historyApiFallback: {
      rewrites: [
        {from: /^\/license$/, to: '/license.html'},
        {from: /^\/error$/, to: '/error.html'},
      ]
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8090/'
      }
    }
  },
  eslint: {
    configFile: './.eslintrc.json'
  }
};
