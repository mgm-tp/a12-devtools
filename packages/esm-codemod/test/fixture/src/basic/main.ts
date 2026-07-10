import Fs from "fs";
import { inspect } from "node:util";

import Data from "./data.json";
import { sum } from "./math";
import { multiple } from "./math/multiple";
import { ONE } from "./one.js";

console.log(Fs);
console.log(ONE);
console.log(inspect(Data));

console.log(sum(1, 2));
console.log(multiple(3, 4));