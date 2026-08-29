import type { VercelRequest, VercelResponse } from "@vercel/node";

const GEMINI_MODEL = "gemini-2.5-flash";
const GEMINI_URL = `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`;

function sendError(res: VercelResponse, status: number, error: string) {
  return res.status(status).json({ success: false, error });
}

export default async function handler(req: VercelRequest, res: VercelResponse) {
  console.log("[AI] request", {
    method: req.method,
    hasBody: req.body != null,
    contentLength: typeof req.body?.content === "string" ? req.body.content.length : 0,
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
  if (!content.trim()) {
    console.warn("[AI] empty content");
    return sendError(res, 400, "Content must not be empty");
  }

  try {
    console.log("[AI] calling Gemini", { model: GEMINI_MODEL });

    const response = await fetch(`${GEMINI_URL}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
      },
      body: JSON.stringify({
        contents: [
          {
            role: "user",
            parts: [{ text: content }],
          },
        ],
      }),
      signal: AbortSignal.timeout(30_000),
    });

    console.log("[AI] Gemini response", { status: response.status, ok: response.ok });

    if (!response.ok) {
      const upstreamBody = await response.text();
      console.error("[AI] Gemini error", {
        status: response.status,
        body: upstreamBody.slice(0, 1000),
      });
      return sendError(res, 502, "Unable to generate an AI response");
    }

    const data = await response.json() as {
      candidates?: Array<{
        content?: {
          parts?: Array<{ text?: string }>;
        };
      }>;
    };

    const text = data.candidates
      ?.flatMap((candidate) => candidate.content?.parts ?? [])
      .map((part) => part.text ?? "")
      .join("")
      .trim();

    if (!text) {
      console.error("[AI] Gemini returned no text");
      return sendError(res, 502, "AI returned an empty response");
    }

    console.log("[AI] success", { responseLength: text.length });
    return res.status(200).json({ success: true, text });
  } catch (error) {
    console.error("[AI] Gemini request failed", {
      error: error instanceof Error ? error.message : String(error),
    });
    return sendError(res, 502, "Unable to generate an AI response");
  }
}
