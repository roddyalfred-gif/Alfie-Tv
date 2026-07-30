import React from 'react';
import ReactDOM from 'react-dom/client';

(globalThis as typeof globalThis & { React: typeof React }).React = React;
(globalThis as typeof globalThis & { ReactDOM: typeof ReactDOM }).ReactDOM = ReactDOM;
