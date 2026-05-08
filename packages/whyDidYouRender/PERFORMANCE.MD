# Analyzing re-rendering of react components

## WhyDidYouRender (WDYR)

WDYR https://github.com/welldone-software/why-did-you-render monkey-patches React to detect unnecessary re-renderings. This page documents some notes/findings for working with it (in the context of A12 Client).

### Notes

By default, WDYR doesn't know about "non-standard" hooks: https://github.com/welldone-software/why-did-you-render#custom-hooks

But WDYR can learn about new hooks using e.g. trackExtraHooks: [[ReactRedux, "useSelector"]] - Note: To work around the "readonly" error (https://github.com/welldone-software/why-did-you-render/issues/85), Webpack can be configured to import react-redux from "lib"

Even with activated track extra hooks, the components using hooks must also be tracked. Reason is this expression in shouldTrack. Hook changes are only tracked when the component is also tracked (i.e. pure and pure tracking activated OR explicitly tracked):

```ts
!!(
	Component.whyDidYouRender ||
	(wdyrStore.options.trackAllPureComponents &&
		((Component &&
			Component.prototype instanceof wdyrStore.React.PureComponent) ||
			isMemoComponent(Component))) ||
	shouldInclude(displayName)
);
```

WDYR `trackAllPureComponents` is a shortcut to track all pure (`extends PureComponent`) and memoized components (`React.memo`) - these are components that have been intentionally optimized - but it has problems

- It also reports problems from other packages (widgets), because it works on component level and doesn't know about packages
- it does not track hooks, because hooks are only tracked if the component that uses the hook is tracked - and React.memo wraps the original component, which yields a situation like Memo (tracked) > Component (untracked) > Hook (untracked)

So in order to track a hook, the immediate parent component of the hook must be tracked explicitly.

## When to optimize

There are different kinds of "unnecessary renders" that need to be handled differently:

Renders caused by Redux state changes in unrelated Activities or even outside of the Activities slice: These should always be avoided. Applications that keep a lot of data inside the store, e.g. for (quasi) offline operation over an extended period of time, will suffer from such renderings, because the state often changes without any change in the Activity View(s), e.g. a data dispatcher sync.

Renders caused by Redux state changes inside of the related Activity, but unrelated parts of it. These renderings normally happen during a related user interaction and are therefore somehow "expected". They should still be avoided if they lead to many React elements being rendered or occur rapidly (e.g. on scroll or keypress or animations).

This page contains more information: https://medium.com/welldone-software/react-when-should-pure-components-be-used-56c4428fe970

## How to optimize

First, make sure that the component you want to optimize is reported by WDYR. If not:

1. Trigger the render

- In the React devtools, activate "Highlight updates when components render"
- If you want to optimize renders in unrelated slices: Click the "trigger store update" button in the sample app's footer.
- If you want to optimize renders within used slices: In this case what you have to do varies. But you already know how to trigger the render because you somehow noticed when it happens

2. Make WDYR report it

- If the render is caused by a props identity change (deep equal but !==) => React.memo the component
- If the render is caused by a useSelector identity change => activate tracking of the component by explicitly adding it - important: you must track the component that uses the hook, not any ancestor of it!

Taking all findings into account, it might be the best option to mark each component for tracking individually, and not use `trackAllPureComponents`. That prop only works with exclusions (even from packages you don't/can't have under control). And rendering optimization should be a conscious, explicit decision.

How to track a component: Set the `whyDidYouRender` prop directly below the function, e.g.

```ts
function MyComp() {}
MyComp.whyDidYouRender = true;
```

This way, everybody immediately is reminded that you must be careful not to break the optimization!

Keep in mind: Hooks can also cause renders!

- `useContext` will render when the context object changes
- `useSelector` will render when the selected object changes
