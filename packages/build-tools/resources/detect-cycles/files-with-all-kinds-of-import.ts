import "module1";

import defaultExport1 from "module2";
import * as name1 from "module3";
import { export1 } from "module4";
import { export1 as alias1 } from "module5";
import { export2, export3 } from "module6";
import defaultExport2, * as name2 from "module7";

var promise = import("module8");
var name4 = require("module9");

// The following exports are reexports and therefore also imports!
export * from "module10";
export { import1, import2 } from "module11";
export { import1 as alias2, import2 as alias3 } from "module12";
export { default } from "module13";

import type { a } from "module14";
import type { b as c } from "module15";
import type { d, e, f } from "module16";

import { type a2 } from "module17";
import { type b2 as c2 } from "module18";
import { type d2, type e2, type f2 } from "module19";

import type * as g from "module20";

import type g2 from "module21";

export type { h } from "module22";
export type { i as j } from "module23";
export type { k, l, m } from "module24";

export { type h2 } from "module25";
export { type i2 as j2 } from "module26";
export { type k2, type l2, type m2 } from "module27";

export type * from "module28";
export type * as n from "module29";
