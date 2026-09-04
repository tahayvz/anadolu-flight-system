# Spring AI, measured

This branch replaces nothing. It **adds** a fourth provider, `spring-ai`, next to the
three the main branch already has, so the two approaches can be compared against the
same tools, the same tests and the same service.

Main branch: the tool-calling loop is written by hand in `Agent.java`.
This branch: `SpringAiChatModel` hands the same tools to Spring AI's `ChatClient`
and lets the framework run the loop.

## What adopting it cost

**Spring Boot 3.2.1 → 3.4.5, and Spring Cloud 2023.0.0 → 2024.0.1.** Spring AI 1.0.0
is built against Boot 3.4.5; there is no version of it that runs on 3.2. Spring Cloud
has to move with Boot, so the gateway came along for the ride. Five modules, one
version bump each.

Three things broke. All three were worth finding:

**1. A pinned version that had gone stale.** The parent POM pinned
`micrometer-tracing` to 1.2.2 — the version that shipped with Boot 3.2. Boot manages
that dependency itself, so the pin was redundant on the day it was written. On the
upgrade it stopped being redundant and started being wrong: it held micrometer at
1.2.2 while Boot 3.4 expected 1.4.x, and the context died with

```
NoSuchMethodError: BraveBaggageManager.<init>(java.util.List, java.util.List)
```

A version pin that merely repeats what the parent already manages is invisible until
an upgrade, and then it is the upgrade's fault-looking failure. The pin is gone; Boot
manages it.

**2. A bean name collision.** Our class is `OllamaChatModel`. Spring AI's
autoconfiguration registers a bean called `ollamaChatModel` too, and Spring refused to
start:

```
BeanDefinitionOverrideException: Invalid bean definition with name 'ollamaChatModel'
```

Ours now declares its bean name explicitly. Bringing in a framework means its class
names are suddenly in your namespace.

**3. A conditional that silently did nothing.** `SpringAiChatModel` was first written
as `@Component @ConditionalOnBean(ChatClient.Builder.class)`. It never registered — no
error, no warning, it simply was not in the model list. `@ConditionalOnBean` is only
meaningful inside autoconfiguration classes: component scanning runs *before*
autoconfiguration, so at the moment the condition is evaluated the builder does not
exist yet and the answer is always no.

This is the same failure shape as an earlier bug in this repo, where a bean sat behind
a cache-related condition and the only real test class never ran. A condition that is
wrong fails by being absent, and absence does not announce itself.

## What it bought

`SpringAiChatModel` is about 40 lines against roughly 150 for the hand-written model
plus the loop. Switching providers becomes a dependency and a property. Retries,
observation and tool binding come with the starter rather than being written.

Cost on the other side: 17 jars, 4.5 MB.

## What it took away

`ChatClient` runs the tool-calling loop inside `call()` and returns only the final
text. Three things that were ours are now the framework's:

| | Hand-written | Spring AI |
|---|---|---|
| Which tools ran | returned in `steps` | not exposed — `steps` is empty |
| Turn limit | ours, configurable, tested | framework's |
| Unknown or failing tool | described back to the model in words, loop continues | framework's behaviour |

The first one is the one that matters. Without the list of tool calls, a wrong answer
cannot be traced; you are left guessing which step went sideways. That is the whole
reason `steps` exists in the response.

## Where this leaves it

Both live side by side here and all 73 tests pass on both. The hand-written loop stays
the default on main: it needs no Boot upgrade, its Gemini path is an API key rather
than a Google Cloud project, and it can explain how it reached an answer.

Spring AI is the better choice once the provider list grows, or when the loop stops
being the interesting part. It was not the better choice for understanding what the
loop does — which was the point of writing it.
