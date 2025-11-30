var https = require('https');
var express = require('express');
var cors = require('cors')
var app = express();

// Step 1: Create & configure a webpack compiler
var webpack = require('webpack');
const {sign} = require("node:crypto");
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

const crypto = require('crypto');
const forge = require('node-forge');
const fs = require('fs');

// Generate self-signed certificate
const pki = forge.pki;
const keys = pki.rsa.generateKeyPair(2048);
const cert = pki.createCertificate();

cert.publicKey = keys.publicKey;
cert.serialNumber = '01';
cert.validity.notBefore = new Date();
cert.validity.notAfter = new Date();
cert.validity.notAfter.setFullYear(cert.validity.notBefore.getFullYear() + 1);

const attrs = [{
  name: 'commonName',
  value: 'localhost'
}, {
  name: 'organizationName',
  value: 'Test'
}];

cert.setSubject(attrs);
cert.setIssuer(attrs);
cert.sign(keys.privateKey);

const options = {
  key: forge.pki.privateKeyToPem(keys.privateKey),
  cert: forge.pki.certificateToPem(cert)
};

// Do anything you like with the rest of your express application.

var hmr = https.createServer(options, app);
hmr.listen(8090, "127.0.0.1", function () {
  console.log('Listening on %j', hmr.address());
});
