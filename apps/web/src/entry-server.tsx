import { createHandler, StartServer } from "@solidjs/start/server";

export default createHandler(() => (
  <StartServer document={({ assets, children, scripts }) => (
    <html lang="en">
      <head>
        <meta charset="utf-8" />
        <meta name="viewport" content="width=device-width, initial-scale=1" />
        <meta name="theme-color" content="#f5f0e6" />
        <title>Cat Care — A calm view of today</title>
        <meta name="description" content="A calm local companion for your cat's care responsibilities." />
        {assets}
      </head>
      <body><div id="app">{children}</div>{scripts}</body>
    </html>
  )} />
));
