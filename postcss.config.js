module.exports = {
  plugins: [
    require('@tailwindcss/postcss'),
    require('postcss-preset-env')({
      browsers: 'safari >= 15.4, edge >= 100, chrome >= 109',
    }),
  ]
}
