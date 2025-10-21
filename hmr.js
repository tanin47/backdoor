var http = require('http');
var express = require('express');
var cors = require('cors')
var app = express();

// Step 1: Create & configure a webpack compiler
var webpack = require('webpack');
var webpackConfig = require('./webpack.config.js')({}, {mode: 'development'});
var compiler = webpack(webpackConfig);

app.use(cors())
// Step 2: Attach the dev middleware to the compiler & the server
app.use(
    require('webpack-dev-middleware')(compiler, {
        publicPath: webpackConfig.output.publicPath,
    })
);

// Step 3: Attach the hot middleware to the compiler & the server
app.use(
    require('webpack-hot-middleware')(compiler, {
        log: console.log,
        path: '/__webpack_hmr',
        heartbeat: 10 * 1000,
    })
);

// Do anything you like with the rest of your express application.

var hmr = http.createServer(app);
hmr.listen(8090, "127.0.0.1", function () {
    console.log('Listening on %j', hmr.address());
});
