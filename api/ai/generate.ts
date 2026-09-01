import type { VercelRequest, VercelResponse } from "@vercel/node";

const DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite";
const EMBEDDING_MODELS = new Set(["gemini-embedding-001", "gemini-embedding-2"]);

function sendError(res: VercelResponse, status: number, error: string) {
  return res.status(status).json({ success: false, error });
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  const requestedModel = typeof req.body?.model === "string" ? req.body.model.trim() : "";
  const model = requestedModel || DEFAULT_GEMINI_MODEL;
  const isEmbedding = EMBEDDING_MODELS.has(model);

  console.log("[AI] request", {
    method: req.method,
    model,
    mode: isEmbedding ? "embedding" : "generation",
    hasBody: req.body != null,
    contentLength: typeof req.body?.content === "string" ? req.body.content.length : 0,
    promptLength: typeof req.body?.prompt === "string" ? req.body.prompt.length : 0,
    hasGeminiKey: Boolean(process.env.GEMINI_API_KEY),
  });

  if (req.method !== "POST") {
    res.setHeader("Allow", "POST");
    return sendError(res, 405, "Method not allowed");
  }

  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) {
    console.error("[AI] GEMINI_API_KEY is missing");
    return sendError(res, 500, "AI service is not configured");
  }

  const content = typeof req.body?.content === "string" ? req.body.content : "";
  const prompt = typeof req.body?.prompt === "string" ? req.body.prompt.trim() : "";
  if (!content.trim()) {
    console.warn("[AI] empty content");
    return sendError(res, 400, "Content must not be empty");
  }

  try {
    const generationInput = prompt
      ? `Prompt:\n${prompt}\n\nContent:\n${content}`
      : content;
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${model}:${isEmbedding ? "embedContent" : "generateContent"}`;
    const body = isEmbedding
      ? {
          model: `models/${model}`,
          content: {
            parts: [{ text: content }],
          },
          output_dimensionality: 3072,
        }
      : {
          contents: [
            {
              role: "user",
              parts: [{ text: generationInput }],
            },
          ],
        };

    console.log("[AI] calling Gemini", { model, mode: isEmbedding ? "embedding" : "generation" });

    const response = await fetch(endpoint, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(60_000),
    });

    console.log("[AI] Gemini response", { model, status: response.status, ok: response.ok });

    if (!response.ok) {
      const upstreamBody = await response.text();
      console.error("[AI] Gemini error", {
        model,
        status: response.status,
        body: upstreamBody.slice(0, 1000),
      });
      return sendError(res, 502, "Unable to generate an AI response");
    }

    const data = await response.json() as {
      embedding?: {
        values?: number[];
      };
      candidates?: Array<{
        content?: {
          parts?: Array<{ text?: string }>;
        };
      }>;
    };

    if (isEmbedding) {
      const values = data.embedding?.values;
      if (!values?.length) {
        console.error("[AI] Gemini returned no embedding values", { model });
        return sendError(res, 502, "AI returned an empty embedding");
      }
      const text = [
        `Embedding model: ${model}`,
        `Dimensions: ${values.length}`,
        "",
        `Vector:\n[${values.join(", ")}]`,
      ].join("\n");
      console.log("[AI] embedding success", { model, dimensions: values.length, responseLength: text.length });
      return res.status(200).json({ success: true, text });
    }

    const text = data.candidates
      ?.flatMap((candidate) => candidate.content?.parts ?? [])
      .map((part) => part.text ?? "")
      .join("")
      .trim();

    if (!text) {
      console.error("[AI] Gemini returned no text", { model });
      return sendError(res, 502, "AI returned an empty response");
    }

    console.log("[AI] success", { model, responseLength: text.length });
    return res.status(200).json({ success: true, text });
  } catch (error) {
    console.error("[AI] Gemini request failed", {
      model,
      error: error instanceof Error ? error.message : String(error),
    });
    return sendError(res, 502, "Unable to generate an AI response");
  }
}
