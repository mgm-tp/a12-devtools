import { inspect } from "node:util";
import Fs from "fs";

import actionCreatorFactory, { type ActionCreator } from "typescript-fsa";
import styled, { css } from "styled-components";
import React from "react/jsx-runtime";

import { HashMap } from "@com.mgmtp.a12.utils/utils-collections";
import { depthFirstSearch } from "@com.mgmtp.a12.utils/utils-collections/lib/search";
import { DirectedGraph } from "@com.mgmtp.a12.utils/utils-collections/lib/DirectedGraph";

import { sum } from "./math";
import { multiple } from "./math/multiple";
import { ONE } from "./one.js";
import Data from "./data.json";

console.log(React);

console.log(Fs);
console.log(ONE);
console.log(inspect(Data));

console.log(sum(1, 2));
console.log(multiple(3, 4));

console.log(HashMap);
console.log(depthFirstSearch);
console.log(DirectedGraph);

console.log(actionCreatorFactory);
console.log({} as ActionCreator<unknown>);

console.log(
  styled.div(() => {
    return css`
      width: 100%;
    `;
  }),
);
