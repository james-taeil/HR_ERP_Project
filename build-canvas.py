#!/usr/bin/env python3
"""specs/*/spec.md 에서 요구사항을 추출해 Cursor 캔버스 파일을 생성한다.

사용법: python3 build-canvas.py [출력경로]
출력경로를 생략하면 현재 폴더에 HR-ERP-requirements.canvas.tsx 를 만든다.
Cursor에서 열려면 ~/.cursor/projects/<워크스페이스>/canvases/ 아래에 두어야 한다.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SPECS = ROOT / "specs"
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "HR-ERP-requirements.canvas.tsx"

TITLES = {
    "010-인사관리": "인사관리",
    "020-근태관리": "근태관리",
    "030-노무관리": "노무관리",
    "040-보험관리": "보험관리",
    "050-기타소득관리": "기타소득관리",
    "060-세무관리": "세무관리",
    "070-교육운영관리": "교육운영관리",
    "900-플랫폼기반": "플랫폼 기반",
}

domains = []
for key in TITLES:
    text = (SPECS / key / "spec.md").read_text(encoding="utf-8")
    groups, current = [], None
    for line in text.splitlines():
        g = re.match(r"^### 5\.\d+ (.+)$", line)
        if g:
            current = {"title": g.group(1).strip(), "items": []}
            groups.append(current)
            continue
        m = re.match(r"^\*\*(FR-\d+)\*\* (.+)$", line)
        if m and current is not None:
            current["items"].append([m.group(1), m.group(2).strip()])
    groups = [g for g in groups if g["items"]]
    code, name = key.split("-", 1)
    domains.append(
        {
            "code": code,
            "name": TITLES[key],
            "path": str(SPECS / key / "spec.md"),
            "groups": groups,
            "fr": sum(len(g["items"]) for g in groups),
            "ec": len(re.findall(r"^- \*\*EC-", text, re.M)),
            "nfr": len(re.findall(r"^- \*\*NFR-", text, re.M)),
            "q": len(re.findall(r"^- \*\*Q-", text, re.M)),
        }
    )

OPEN_Q = [
    ["Q-3", "데이터베이스", "MySQL 8 + Redis 대 PostgreSQL", "인덱스와 캐시 전략"],
    ["Q-4", "모바일 앱", "React Native 포함 대 웹만", "알림 채널과 배포"],
    ["Q-6", "경비 청구", "v1.0 8건 존치 여부", "7개 분류 밖 기능"],
    ["Q-7", "기관 신고 방식", "서식 출력 대 EDI 파일", "보험·세무의 종착점"],
    ["Q-8", "AI 모델 제공자", "미정", "민감정보 전송 범위와 비용"],
    ["Q-9", "겸직 허용", "확정: 단수 소속", "닫힘"],
]

DECIDED = [
    ["기간 제약 없음", "단계별 기능 절단선을 두지 않는다"],
    ["단일 기업 모델", "회사 1개 아래 사업장과 부서. 대행 사무소 계층 없음"],
    ["겸직 없음", "사원은 사업장 1개, 부서 1개에만 소속"],
    ["급여는 060에 포함", "별도 도메인을 두지 않는다. 요구사항은 미작성"],
]

tpl = '''import {
  Callout,
  Card,
  CardBody,
  CardHeader,
  Divider,
  H1,
  H2,
  Pill,
  Row,
  Spacer,
  Stack,
  Stat,
  Table,
  Text,
  useHostTheme,
  useState,
} from "cursor/canvas";

type Group = { title: string; items: [string, string][] };
type Domain = {
  code: string;
  name: string;
  path: string;
  groups: Group[];
  fr: number;
  ec: number;
  nfr: number;
  q: number;
};

const DOMAINS: Domain[] = __DOMAINS__;
const OPEN_Q: string[][] = __OPEN_Q__;
const DECIDED: string[][] = __DECIDED__;

const TOTAL_FR = DOMAINS.reduce((s, d) => s + d.fr, 0);
const TOTAL_EC = DOMAINS.reduce((s, d) => s + d.ec, 0);
const TOTAL_NFR = DOMAINS.reduce((s, d) => s + d.nfr, 0);
const TOTAL_Q = DOMAINS.reduce((s, d) => s + d.q, 0);

function RequirementRow({ id, text }: { id: string; text: string }) {
  const theme = useHostTheme();
  return (
    <Row gap={10} align="start" style={{ padding: "3px 0" }}>
      <Text
        size="small"
        tone="tertiary"
        style={{
          fontVariantNumeric: "tabular-nums",
          minWidth: 52,
          flexShrink: 0,
          color: theme.text.quaternary,
        }}
      >
        {id}
      </Text>
      <Text size="small" style={{ minWidth: 0 }}>
        {text}
      </Text>
    </Row>
  );
}

function DomainSection({ domain, open }: { domain: Domain; open: boolean }) {
  const theme = useHostTheme();
  return (
    <Card collapsible defaultOpen={open}>
      <CardHeader
        trailing={
          <Text size="small" tone="tertiary">
            {`요구사항 ${domain.fr} · 엣지 ${domain.ec} · 비기능 ${domain.nfr}`}
          </Text>
        }
      >
        {`${domain.code} ${domain.name}`}
      </CardHeader>
      <CardBody>
        <Stack gap={14}>
          {domain.groups.map((g, gi) => (
            <Stack key={g.title} gap={2}>
              {gi > 0 ? <Divider style={{ marginBottom: 8 }} /> : null}
              <Text
                size="small"
                weight="semibold"
                style={{ color: theme.accent.primary, marginBottom: 4 }}
              >
                {g.title}
              </Text>
              {g.items.map((it) => (
                <RequirementRow key={it[0]} id={it[0]} text={it[1]} />
              ))}
            </Stack>
          ))}
        </Stack>
      </CardBody>
    </Card>
  );
}

export default function RequirementsCanvas() {
  const [selected, setSelected] = useState<string>("all");
  const visible =
    selected === "all" ? DOMAINS : DOMAINS.filter((d) => d.code === selected);

  return (
    <Stack gap={20} style={{ padding: 24, maxWidth: 980 }}>
      <Stack gap={6}>
        <H1>인사 · 노무 · 세무 통합 관리 시스템</H1>
        <Text tone="secondary">
          요구사항 정의서 v2.0 · 기존 v1.0 문서 122건과 캔버스 초안 219건을
          병합하고 중복을 제거해 7개 업무 도메인과 플랫폼 기반으로 재구성했다.
          모든 요구사항에 수용 기준이 1대1로 대응한다.
        </Text>
      </Stack>

      <Row gap={28} align="end">
        <Stat value={TOTAL_FR} label="기능 요구사항" />
        <Stat value={TOTAL_FR} label="수용 기준" />
        <Stat value={TOTAL_EC} label="엣지 케이스" />
        <Stat value={TOTAL_NFR} label="비기능 요구사항" />
        <Spacer />
        <Stat value={TOTAL_Q + 5} label="열린 질문" tone="warning" />
      </Row>

      <Callout tone="warning" title="급여 계산 요구사항이 아직 없다">
        급여를 060 세무관리에 포함하기로 확정했으나 해당 절은 범위만 잡혀 있다.
        근태가 산출한 시간 구분, 보험의 사원 부담분, 세무의 원천징수가 모두 급여를
        거쳐 흐르므로 계산 사슬 한가운데가 비어 있는 상태다.
      </Callout>

      <Stack gap={10}>
        <Row gap={6} wrap>
          <Pill active={selected === "all"} onClick={() => setSelected("all")}>
            전체
          </Pill>
          {DOMAINS.map((d) => (
            <Pill
              key={d.code}
              active={selected === d.code}
              onClick={() => setSelected(d.code)}
            >
              {`${d.code} ${d.name}`}
            </Pill>
          ))}
        </Row>
        <Stack gap={8}>
          {visible.map((d) => (
            <DomainSection
              key={d.code}
              domain={d}
              open={selected === d.code}
            />
          ))}
        </Stack>
      </Stack>

      <Stack gap={8}>
        <H2>확정된 설계 결정</H2>
        <Text size="small" tone="secondary">
          아래 결정이 모든 도메인 스펙의 전제다. 이 중 하나가 바뀌면 하위 스펙도
          함께 고쳐야 한다.
        </Text>
        <Table
          headers={["결정", "내용"]}
          rows={DECIDED}
          columnAlign={["left", "left"]}
        />
      </Stack>

      <Stack gap={8}>
        <H2>제품 수준 열린 질문</H2>
        <Text size="small" tone="secondary">
          병합 과정에서 두 원본이 다르게 정한 항목. 설계 문서 착수 전까지 닫으면
          된다. 도메인별 질문 {TOTAL_Q}개는 각 스펙 문서에 따로 남아 있다.
        </Text>
        <Table
          headers={["ID", "질문", "상태", "영향"]}
          rows={OPEN_Q}
          columnAlign={["left", "left", "left", "left"]}
          rowTone={[
            undefined,
            undefined,
            undefined,
            undefined,
            undefined,
            "success",
          ]}
        />
      </Stack>
    </Stack>
  );
}
'''

src = (
    tpl.replace("__DOMAINS__", json.dumps(domains, ensure_ascii=False, indent=2))
    .replace("__OPEN_Q__", json.dumps(OPEN_Q, ensure_ascii=False))
    .replace("__DECIDED__", json.dumps(DECIDED, ensure_ascii=False))
)
OUT.write_text(src, encoding="utf-8")

# 자기 점검: 추출 건수가 스펙 원본의 FR 수와 맞는지 확인한다.
raw = sum(
    len(re.findall(r"^\*\*FR-", (SPECS / k / "spec.md").read_text(encoding="utf-8"), re.M))
    for k in TITLES
)
got = sum(d["fr"] for d in domains)
assert raw == got, f"추출 누락: 원본 {raw}건, 추출 {got}건"
print(f"{OUT}  도메인 {len(domains)}개, 요구사항 {got}건, {len(src)}바이트")
