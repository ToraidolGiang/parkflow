import config from '../mock/student-config.json'
import recommendation from '../mock/student-recommendation.json'
import type { StudentConfig, StudentRecommendation, StudentSelection } from '../types'

const delay = () => new Promise((resolve) => setTimeout(resolve, 40))
export async function getStudentConfig(): Promise<StudentConfig> { await delay(); return structuredClone(config) as StudentConfig }
export async function getStudentRecommendation(_selection: StudentSelection, _sessionId: string): Promise<StudentRecommendation> {
  void _selection; void _sessionId
  await delay(); return structuredClone(recommendation) as StudentRecommendation
}
